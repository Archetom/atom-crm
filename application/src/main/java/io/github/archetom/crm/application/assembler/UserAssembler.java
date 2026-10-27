package io.github.archetom.crm.application.assembler;

import io.github.archetom.crm.api.dto.response.UserPageResponse;
import io.github.archetom.crm.api.dto.response.UserResponse;
import io.github.archetom.crm.application.vo.UserVO;
import io.github.archetom.crm.domain.entity.User;
import io.github.archetom.crm.domain.model.UserStatus;
import io.github.archetom.crm.domain.repository.PageResult;
import io.github.archetom.crm.domain.valueobject.Email;
import io.github.archetom.crm.domain.valueobject.PhoneNumber;
import io.github.archetom.crm.domain.valueobject.TenantId;
import io.github.archetom.crm.domain.valueobject.UserId;
import io.github.archetom.crm.domain.valueobject.Username;
import io.github.archetom.crm.shared.util.PageUtil;
import io.github.archetom.common.result.Pager;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.List;

/** Maps domain output to application and tenant-safe public representations. */
@Mapper
public interface UserAssembler {

    UserAssembler INSTANCE = Mappers.getMapper(UserAssembler.class);

    /**
     * Domain User -> UserVO
     */
    @Mapping(target = "id", expression = "java(userIdToLong(user.getId()))")
    @Mapping(target = "username", expression = "java(usernameToString(user.getUsername()))")
    @Mapping(target = "email", expression = "java(emailToString(user.getEmail()))")
    @Mapping(target = "maskedPhoneNumber", expression = "java(phoneNumberToMasked(user.getPhoneNumber()))")
    @Mapping(target = "status", expression = "java(statusToCode(user.getStatus()))")
    @Mapping(target = "statusName", expression = "java(statusToName(user.getStatus()))")
    @Mapping(target = "active", expression = "java(statusToActive(user.getStatus()))")
    @Mapping(target = "tenantId", expression = "java(tenantIdToLong(user.getTenantId()))")
    UserVO toVO(User user);

    /**
     * UserVO -> UserResponse
     */
    @BeanMapping(ignoreUnmappedSourceProperties = "tenantId")
    UserResponse toResponse(UserVO userVO);

    /**
     * Domain User -> UserResponse
     */
    default UserResponse toResponse(User user) {
        return toResponse(toVO(user));
    }

    /**
     * Domain User List -> UserVO List
     */
    List<UserVO> toVOList(List<User> users);

    /**
     * UserVO List -> UserResponse List
     */
    List<UserResponse> toResponseList(List<UserVO> userVOs);

    /**
     * Domain User Pager -> UserVO Pager
     */
    default Pager<UserVO> toVOPager(PageResult<User> userPager) {
        if (userPager == null) {
            return null;
        }

        Pager<UserVO> voPager = new Pager<>();
        voPager.setPageNum(userPager.page());
        voPager.setPageSize(userPager.size());
        voPager.setTotalNum(userPager.total());
        voPager.setObjectList(toVOList(userPager.items()));
        return voPager;
    }

    /**
     * UserVO Pager -> UserResponse Pager
     */
    default Pager<UserResponse> toResponsePager(Pager<UserVO> userVOPager) {
        if (userVOPager == null) {
            return null;
        }
        if (userVOPager.getTotalNum() < 0) {
            throw new IllegalStateException("Unknown total (" + userVOPager.getTotalNum()
                    + ") must not reach the public page contract; model count-free paging explicitly.");
        }

        Pager<UserResponse> responsePager = PageUtil.copy(userVOPager);
        // The public page contract exposes pageNum/pageSize/totalNum/objectList only
        // (docs/api-reference.md); meta is an internal carrier and never crosses the
        // API boundary — same posture as the tenantId strip in toResponse.
        responsePager.setMeta(null);
        responsePager.setObjectList(toResponseList(userVOPager.getObjectList()));
        return responsePager;
    }

    /** Converts the internal pager carrier to the explicit public User page contract. */
    default UserPageResponse toPageResponse(Pager<UserVO> userVOPager) {
        Pager<UserResponse> responsePager = toResponsePager(userVOPager);
        if (responsePager == null) {
            return null;
        }
        return new UserPageResponse()
                .setPageNum(Math.toIntExact(responsePager.getPageNum()))
                .setPageSize(Math.toIntExact(responsePager.getPageSize()))
                .setTotalNum(responsePager.getTotalNum())
                .setObjectList(responsePager.getObjectList());
    }

    default Long userIdToLong(UserId userId) {
        return userId != null ? userId.getValue() : null;
    }

    default Long tenantIdToLong(TenantId tenantId) {
        return tenantId != null ? tenantId.getValue() : null;
    }

    default String usernameToString(Username username) {
        return username != null ? username.getValue() : null;
    }

    default String emailToString(Email email) {
        return email != null ? email.getValue() : null;
    }

    default String phoneNumberToMasked(PhoneNumber phoneNumber) {
        return phoneNumber != null ? phoneNumber.getMasked() : null;
    }

    default String statusToCode(UserStatus status) {
        return status != null ? status.getCode() : null;
    }

    default String statusToName(UserStatus status) {
        return status != null ? status.getDisplayName() : null;
    }

    default Boolean statusToActive(UserStatus status) {
        return status == UserStatus.ACTIVE;
    }
}
