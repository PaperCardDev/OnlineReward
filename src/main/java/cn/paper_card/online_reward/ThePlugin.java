package cn.paper_card.online_reward;

import cn.paper_card.database.api.DatabaseApi;
import cn.paper_card.paper_online_time.api.OnlineTimeAndJoinCount;
import cn.paper_card.paper_online_time.api.PlayerOnlineTimeApi;
import cn.paper_card.player_coins.api.PlayerCoinsApi;
import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

public final class ThePlugin extends JavaPlugin implements Listener {

    private final @NotNull TaskScheduler taskScheduler;

    private RewardServiceImpl rewardService = null;

    private PlayerOnlineTimeApi playerOnlineTimeApi = null;

    private PlayerCoinsApi playerCoinsApi = null;

    public ThePlugin() {
        this.taskScheduler = UniversalScheduler.getScheduler(this);
    }

    @Override
    public void onEnable() {
        final DatabaseApi api = this.getServer().getServicesManager().load(DatabaseApi.class);
        if (api == null) throw new RuntimeException("无法连接到DatabaseApi！");

        this.playerOnlineTimeApi = this.getServer().getServicesManager().load(PlayerOnlineTimeApi.class);
        if (this.playerOnlineTimeApi == null) throw new RuntimeException("无法连接到PlayerOnlineTimeApi");

        this.playerCoinsApi = this.getServer().getServicesManager().load(PlayerCoinsApi.class);
        if (this.playerCoinsApi == null) throw new RuntimeException("无法连接到PlayerCoinsApi");

        this.rewardService = new RewardServiceImpl(api.getRemoteMySQL().getConnectionNormal());

        this.getServer().getPluginManager().registerEvents(this, this);

    }

    @Override
    public void onDisable() {
        this.taskScheduler.cancelTasks(this);

        if (this.rewardService != null) {
            try {
                this.rewardService.close();
            } catch (SQLException e) {
                getSLF4JLogger().error("", e);
            }
        }
    }

    static long getTodayBeginTime(long cur) {
        final long delta = (cur + TimeZone.getDefault().getRawOffset()) % (24 * 60 * 60 * 1000L);
        return cur - delta;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        // 尝试结算上一天的硬币
        final RewardServiceImpl service = this.rewardService;
        assert service != null;

        final PlayerOnlineTimeApi api = this.playerOnlineTimeApi;
        assert api != null;

        final PlayerCoinsApi api1 = this.playerCoinsApi;
        assert api1 != null;

        this.taskScheduler.runTaskAsynchronously(() -> {
            final Player player = event.getPlayer();
            final long cur = System.currentTimeMillis();
            final long todayBeginTime = getTodayBeginTime(cur);

            final RewardInfo info;

            try {
                info = service.queryOneTimeAfter(player.getUniqueId(), todayBeginTime);
            } catch (SQLException e) {
                getSLF4JLogger().error("", e);
                sendException(player, e);
                return;
            }

            // 已经结算
            if (info != null) {
                getSLF4JLogger().info("玩家%s已经结算昨日的在线奖励".formatted(player.getName()));
                return;
            }

            // 结算

            final OnlineTimeAndJoinCount onlineInfo;

            try {
                onlineInfo = api.queryOneDay(player.getUniqueId(), todayBeginTime);
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                this.sendException(player, e);
                return;
            }

            // 每半小时一个硬币
            final long ONE_HOUR = 60 * 60 * 1000L;

            // 记录
            final long hours = onlineInfo.onlineTime() / ONE_HOUR;

            // 换算为硬币
            final long coins = Math.min(hours * 2, 10);

            this.getSLF4JLogger().info("玩家%s昨日在线%d小时，将得到%d枚硬币".formatted(player.getName(), hours, coins));

            // 记录数据，表示已经结算过了
            try {
                service.add(new RewardInfo(
                        player.getUniqueId(),
                        cur,
                        todayBeginTime,
                        coins,
                        "玩家%s昨日（%s）在线%d小时获得%d枚硬币".formatted(
                                player.getName(),
                                new SimpleDateFormat("MM月dd日").format(todayBeginTime - TimeUnit.Day.getMs()),
                                hours,
                                coins
                        )
                ));
            } catch (SQLException e) {
                this.getSLF4JLogger().error("", e);
                this.sendException(player, e);
                return;
            }

            // 增加硬币
            try {
                api1.addCoins(player.getUniqueId(), coins);
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                this.sendException(player, new Exception("尝试为你增加%d枚硬币时异常".formatted(coins), e));
                return;
            }

            // 查询硬币
            final long leftCoins;

            try {
                leftCoins = api1.queryCoins(player.getUniqueId());
            } catch (Exception e) {
                this.getSLF4JLogger().error("", e);
                this.sendException(player, new Exception("已经结算给你%d枚硬币，但是无法查询剩余硬币".formatted(coins), e));
                return;
            }

            // 发送消息
            final TextComponent.Builder text = Component.text();
            this.appendPrefix(text);
            text.appendSpace();
            text.append(Component.text("昨日你累计在线"));
            text.append(Component.text(Util.toReadableTime(onlineInfo.onlineTime())).color(NamedTextColor.AQUA));

            text.appendNewline();
            text.append(Component.text("已赠送给你"));
            text.append(this.coinsNumber(coins));
            text.append(Component.text("枚硬币，你还有"));
            text.append(this.coinsNumber(leftCoins));
            text.append(Component.text("枚硬币~"));
            text.appendSpace();
            text.append(Component.text("[???]")
                    .color(NamedTextColor.GRAY).decorate(TextDecoration.UNDERLINED)
                    .hoverEvent(HoverEvent.showText(Component.text("每在线1小时赠送2枚硬币，不足1小时按0小时计算，最多10枚硬币")))
            );

            player.sendMessage(text.build().color(NamedTextColor.GREEN));
        });
    }

    @NotNull TextComponent coinsNumber(long c) {
        return Component.text(c).color(NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
    }

    void sendException(@NotNull CommandSender sender, @NotNull Throwable e) {
        final TextComponent.Builder text = Component.text();

        this.appendPrefix(text);
        text.appendSpace();

        text.append(Component.text("==== 异常信息 ====").color(NamedTextColor.DARK_RED));

        for (Throwable t = e; t != null; t = t.getCause()) {
            text.appendNewline();
            text.append(Component.text(t.toString()).color(NamedTextColor.RED));
        }

        sender.sendMessage(text.build());
    }

    void appendPrefix(@NotNull TextComponent.Builder text) {
        text.append(Component.text("[").color(NamedTextColor.GRAY));
        text.append(Component.text("在线奖励").color(NamedTextColor.DARK_AQUA));
        text.append(Component.text("]").color(NamedTextColor.GRAY));
    }
}
