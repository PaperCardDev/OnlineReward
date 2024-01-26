package cn.paper_card.online_reward;

import java.util.UUID;

record RewardInfo(
        UUID playerId,
        long time,
        long dayBegin,
        long coins,
        String comment
) {
}