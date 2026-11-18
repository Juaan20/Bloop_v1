package com.bloop.vipservice.service;

import com.bloop.vipservice.entity.VipOrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VipAccountCalculatorTest {

    @Test
    void splitsAnOrderBetweenThePackageAndExtras() {
        assertThat(VipAccountCalculator.split(2, 1)).containsExactly(1, 1);
        assertThat(VipAccountCalculator.split(1, 3)).containsExactly(1, 0);
        assertThat(VipAccountCalculator.split(3, 0)).containsExactly(0, 3);
        assertThat(VipAccountCalculator.split(2, -1)).containsExactly(0, 2);
    }

    @Test
    void ordersMoveOneStepAtATimeAndCanBeCancelledWhileOpen() {
        assertThat(VipOrderStatus.NEW.canChangeTo(VipOrderStatus.PENDING)).isTrue();
        assertThat(VipOrderStatus.NEW.canChangeTo(VipOrderStatus.DELIVERED)).isFalse();
        assertThat(VipOrderStatus.ON_THE_WAY.canChangeTo(VipOrderStatus.DELIVERED)).isTrue();
        assertThat(VipOrderStatus.PREPARING.canChangeTo(VipOrderStatus.CANCELLED)).isTrue();
        assertThat(VipOrderStatus.DELIVERED.canChangeTo(VipOrderStatus.CANCELLED)).isFalse();
        assertThat(VipOrderStatus.DELIVERED.next()).isNull();
    }
}
