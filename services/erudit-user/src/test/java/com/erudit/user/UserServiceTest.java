package com.erudit.user;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.erudit.user.model.*;
import com.erudit.user.repository.UserRepository;
import com.erudit.user.service.UserService;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.Test;
class UserServiceTest {
 private final UserRepository repository=mock(UserRepository.class);private final UserService service=new UserService(repository);private final UUID id=UUID.randomUUID();
 @Test void supportsRegistrationProfileSearchAndSettings(){var user=new User(id,"user@example.com","User",false,OffsetDateTime.now());when(repository.create(any(),any(),any(),any())).thenReturn(user);when(repository.get(id)).thenReturn(Optional.of(user));when(repository.search("u",0,20)).thenReturn(List.of(user));when(repository.count("u")).thenReturn(1L);when(repository.update(id,"New")).thenReturn(user);var settings=new UserSettings(id,"ru",10);when(repository.settings(id)).thenReturn(settings);when(repository.save(any())).thenReturn(settings);
  assertThat(service.register(" USER@EXAMPLE.COM "," User ")).isEqualTo(user);assertThat(service.get(id)).isEqualTo(user);assertThat(service.search("u",0,20).total()).isOne();assertThat(service.update(id," New ")).isEqualTo(user);assertThat(service.settings(id)).isEqualTo(settings);assertThat(service.settings(id,"ru",10)).isEqualTo(settings);service.delete(id);verify(repository).delete(id);
 }
 @Test void validatesVerificationAndPagination(){when(repository.verify("ok")).thenReturn(true);service.verify("ok");when(repository.verify("bad")).thenReturn(false);assertThatThrownBy(()->service.verify("bad")).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->service.search("",0,51)).isInstanceOf(IllegalArgumentException.class);}
}
