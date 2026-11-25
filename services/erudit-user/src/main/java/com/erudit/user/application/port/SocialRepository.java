package com.erudit.user.application.port;

import com.erudit.user.domain.*;
import com.erudit.user.domain.SocialModels.*;
import java.time.OffsetDateTime;
import java.util.*;

public interface SocialRepository {
    UUID request(UUID from,UUID to);
    void decide(UUID owner,UUID id,boolean accept);
    List<PublicUser> friends(UUID user,int page,int size);
    void remove(UUID user,UUID friend);
    Device saveDevice(UUID user,String token,String platform);
    void revokeDevice(UUID user,UUID id);
    InternalUser internal(UUID id);
}
