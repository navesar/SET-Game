package bguspl.set.ex;

import bguspl.set.Env;

import java.util.ArrayList;

public class TimerDealer implements Runnable {
    private Env env;
    private volatile Boolean terminate;

    private Thread timerDealerThread;

    private long reshuffleTime;

    private volatile boolean shouldReset;

    private ArrayList<Player> frozenPlayers;

    public final Object frozenPlayersLock = new Object();

    private final int zero = 0;

    private Player joinMe;

    private Dealer dealer;


    public TimerDealer(Env env, Dealer dealer){
        this.env = env;
        terminate = shouldReset = false;
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        frozenPlayers = new ArrayList<Player>();
        this.dealer = dealer;

    }
    @Override
    public void run() {
        System.out.println("Thread DealerTimer is starting");
        timerDealerThread = Thread.currentThread();
        while(!terminate){
            updateTimerDisplay(shouldReset);
            updatePlayersFreezeTimer();
        }
        if(joinMe != null){
            if(joinMe.playerThread != null) try{ joinMe.playerThread.join();}catch (InterruptedException ignored){}
        }
        System.out.println("info: Thread TimerDealer terminated");
    }

    public Thread getTimerDealerThread(){
        return timerDealerThread;
    }

    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        if(reset) {
            shouldReset = false;
            env.ui.setCountdown(env.config.turnTimeoutMillis, false);
            reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis;
        }
        else {
            long currentTime = reshuffleTime - System.currentTimeMillis();
            if(currentTime >= zero) {
                if (currentTime <= env.config.turnTimeoutWarningMillis)
                    env.ui.setCountdown(currentTime, true);
                else
                    env.ui.setCountdown(currentTime, false);
            }
            else {
                //System.out.println("timer dealer: " + dealer.getElapsedTime());
                env.ui.setElapsed(System.currentTimeMillis() - dealer.getElapsedTime());
            }
        }

    }

    private void updatePlayersFreezeTimer(){
        ArrayList<Player> remove = new ArrayList<>();
        synchronized (frozenPlayersLock) {
            if (frozenPlayers.isEmpty())
                return;
            for (Player p : frozenPlayers) {
                long showInMillis = p.getFreezeTime() - System.currentTimeMillis();
                env.ui.setFreeze(p.id, showInMillis);
                if (showInMillis <= zero) {
                    remove.add(p);
                }
            }
            for (Player p : remove)
                frozenPlayers.remove(p);
        }
    }

    public void terminate(){
        terminate = true;
    }

    public void resetTime(){
        shouldReset = true;
    }

    public void freezePlayer(Player player){
        synchronized (frozenPlayersLock) {
            frozenPlayers.add(player);
        }
    }

    public void setJoinMe(Player one){
        joinMe = one;
    }

    public boolean shouldReset(){ //for testing purpose only
        return  shouldReset;
    }

    public boolean isTerminated(){//for testing purpose only
        return terminate;
    }

}
