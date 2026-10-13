package com.njupt.kms.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class UserQueryPortImpl implements UserQueryPort {

    private final UserMapper userMapper;

    @Override
    public Map<Long, String> nicknamesOf(Collection<Long> userIds) {
        Map<Long, String> result = new HashMap<>();
        if (userIds == null || userIds.isEmpty()) {
            return result;
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return result;
        }
        for (User user : userMapper.selectByIds(ids)) {
            if (StringUtils.hasText(user.getNickname())) {
                result.put(user.getId(), user.getNickname());
            }
        }
        return result;
    }
}
