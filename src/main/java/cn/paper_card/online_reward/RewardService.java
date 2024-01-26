package cn.paper_card.online_reward;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

interface RewardService {
    @Nullable RewardInfo queryOneTimeAfter(@NotNull UUID uuid, long time) throws Exception;

    void add(@NotNull RewardInfo info) throws Exception;

}
