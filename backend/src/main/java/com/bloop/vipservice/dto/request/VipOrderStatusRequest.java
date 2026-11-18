package com.bloop.vipservice.dto.request;

import com.bloop.vipservice.entity.VipOrderStatus;
import jakarta.validation.constraints.NotNull;

public record VipOrderStatusRequest(@NotNull VipOrderStatus status) {
}
