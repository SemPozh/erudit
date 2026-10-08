package com.erudit.friend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class FriendRatingIntegrationTest {
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;

    @Test void managesRequestsAndFriendList() throws Exception {
        UUID alice=createUser("alice"), bob=createUser("bob");
        String response=mvc.perform(post("/api/v1/friends/requests").with(user(alice.toString()))
                .contentType(MediaType.APPLICATION_JSON).content("{\"userId\":\""+bob+"\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data[0].status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        UUID requestId=UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/0/id").asText());
        mvc.perform(get("/api/v1/friends/requests").with(user(bob.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].name").value("alice"));
        mvc.perform(post("/api/v1/friends/requests/{id}/accept",requestId).with(user(alice.toString()))).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/friends/requests/{id}/accept",requestId).with(user(bob.toString()))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/friends").with(user(alice.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].id").value(bob.toString()));
        mvc.perform(delete("/api/v1/friends/{id}",bob).with(user(alice.toString()))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("select count(*) from friend_requests where id=?",Integer.class,requestId)).isZero();
    }

    @Test void ranksByPeriodAndPrivacyAndAmongFriends() throws Exception {
        UUID alice=createUser("rank-a"), bob=createUser("rank-b"), hidden=createUser("rank-hidden");
        profile(alice,100);profile(bob,200);profile(hidden,999);
        jdbc.update("update user_settings set visible_in_rating=false where user_id=?",hidden);
        jdbc.update("insert into friend_requests values (?,?,?,'ACCEPTED',?,?)",UUID.randomUUID(),alice,bob,Timestamp.from(Instant.now()),Timestamp.from(Instant.now()));
        mvc.perform(get("/api/v1/rating").with(user(alice.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(bob.toString())).andExpect(jsonPath("$.data[0].rank").value(1));
        mvc.perform(get("/api/v1/rating/me").with(user(alice.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rank").value(2));
        mvc.perform(get("/api/v1/rating/friends").with(user(alice.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        mvc.perform(get("/api/v1/rating").param("period","YEAR").with(user(alice.toString()))).andExpect(status().isBadRequest());
    }

    private UUID createUser(String name){UUID id=UUID.randomUUID();jdbc.update("insert into users(id,email,password_hash,name,created_at,status,role,email_verified) values (?,?,?,?,?,'ACTIVE','USER',true)",id,name+id+"@test.local","hash",name,java.time.LocalDateTime.now());jdbc.update("insert into user_settings(user_id) values (?)",id);return id;}
    private void profile(UUID id,long points){jdbc.update("insert into user_rating_profiles(user_id,points,er_score,grade_code,updated_at) values (?,?,0,'NOVICE_I',?)",id.toString(),points,Timestamp.from(Instant.now()));}
}

