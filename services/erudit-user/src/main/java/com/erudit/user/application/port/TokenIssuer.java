package com.erudit.user.application.port;

import com.erudit.user.domain.User;
import com.erudit.user.application.AuthenticationService.Token;

public interface TokenIssuer {
    Token issue(User user);
}
