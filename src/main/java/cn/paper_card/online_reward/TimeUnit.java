package cn.paper_card.online_reward;

enum TimeUnit {
    Second(1000L),
    Minute(Second.ms * 60L),
    Hour(Minute.ms * 60L),
    Day(Hour.ms * 24L);

    private final long ms;

    TimeUnit(long ms) {
        this.ms = ms;
    }

    long getMs() {
        return this.ms;
    }

}
