package cn.paper_card.online_reward;

import org.jetbrains.annotations.NotNull;

class Util {
    static @NotNull String toReadableTime(long ms) {

        final long days = ms / TimeUnit.Day.getMs();
        ms %= TimeUnit.Day.getMs();

        final long hours = ms / TimeUnit.Hour.getMs();
        ms %= TimeUnit.Hour.getMs();

        final long minutes = ms / TimeUnit.Minute.getMs();
        ms %= TimeUnit.Minute.getMs();

        final long seconds = ms / TimeUnit.Second.getMs();

        final StringBuilder builder = new StringBuilder();
        if (days != 0) {
            builder.append(days);
            builder.append("天");
        }

        if (hours != 0) {
            builder.append(hours);
            builder.append("时");
        }

        if (minutes != 0) {
            builder.append(minutes);
            builder.append("分");
        }

        if (seconds != 0) {
            builder.append(seconds);
            builder.append("秒");
        }

        final String string = builder.toString();

        return string.isEmpty() ? "0" : string;
    }
}
