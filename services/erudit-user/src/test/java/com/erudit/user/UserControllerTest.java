package com.erudit.user;
import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
import com.erudit.user.controller.UserController;import com.erudit.user.model.*;import com.erudit.user.openapi.model.*;import com.erudit.user.service.UserService;
import java.time.OffsetDateTime;import java.util.*;import org.junit.jupiter.api.Test;
class UserControllerTest {
 @Test void exposesAllUserOperations(){var service=mock(UserService.class);var controller=new UserController(service);UUID id=UUID.randomUUID();var user=new User(id,"a@b.com","Name",true,OffsetDateTime.now());var settings=new UserSettings(id,"ru",10);when(service.register(any(),any())).thenReturn(user);when(service.get(id)).thenReturn(user);when(service.search(null,0,20)).thenReturn(new UserService.Page(List.of(user),1));when(service.update(id,"New")).thenReturn(user);when(service.settings(id)).thenReturn(settings);when(service.settings(id,"en",20)).thenReturn(settings);
  assertThat(controller.register(new RegisterRequest("a@b.com","Name")).getStatusCode().value()).isEqualTo(201);controller.verifyEmail(new VerifyRequest("token"));assertThat(controller.searchUsers(null,0,20).getHeaders().getFirst("X-Total-Count")).isEqualTo("1");assertThat(controller.getUser(id).getBody().getId()).isEqualTo(id);controller.updateUser(id,new UpdateUserRequest("New"));controller.deleteUser(id);controller.getSettings(id);controller.updateSettings(id,new SettingsRequest("en",20));verify(service).delete(id);
 }
}
