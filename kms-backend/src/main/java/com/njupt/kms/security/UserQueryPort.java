package com.njupt.kms.security;

import java.util.Collection;
import java.util.Map;

/**
 * M0 导出的只读端口，供其他模块取用户展示信息。
 *
 * <p>{@code user} 表由 security 包拥有（设计文档 §3.3），其他模块不得直接查询它。
 * 需要作者昵称这类展示数据时，走本接口。
 */
public interface UserQueryPort {

    /** 批量取昵称。昵称为空或用户不存在的 id 不会出现在结果中。 */
    Map<Long, String> nicknamesOf(Collection<Long> userIds);
}
