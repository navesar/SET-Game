package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    public Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    private LinkedList<Integer> tokens; //myField

    private Dealer dealer;

    private volatile boolean gotPoint;

    private volatile boolean gotPenalty;


    private Queue<Integer> pressed;

    private long freezeTime;


    public final Object tokensLock = new Object();

    public final Object waitForPress = new Object();

    private static final int three = 3;

    private static final int zero = 0;

    private  Player joinMe;



    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;
        this.dealer = dealer;
        tokens = new LinkedList<Integer>();
        gotPoint = false;
        gotPenalty = false;
        pressed = new LinkedList<Integer>();
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            // TODO implement main player loop
            if(!human) {
                synchronized (this) {
                    if (gotPoint)
                        pointFreeze();
                    if (gotPenalty)
                        penalty();
                    if (pressed.size() > zero) {
                        int slot = pressed.remove();
                        keyPressed2(slot);
                        notifyAll();
                    } else {
                        if (!terminate) {
                            try {
                                wait();
                            } catch (InterruptedException ignored) {}
                        }
                    }
                }
            }
            else {
                if (gotPoint)
                    pointFreeze();
                if (gotPenalty)
                    penalty();
                if (pressed.size() > zero) {
                    int slot = pressed.remove();
                    keyPressed2(slot);
                }
                else{
                    synchronized (waitForPress){
                        try{
                            waitForPress.wait();
                        }catch (InterruptedException ignored){}
                    }
                }
            }
        }
        if (!human) try {aiThread.join();} catch (InterruptedException ignored) {}
        if(joinMe != null){
        if(joinMe.playerThread != null) try {joinMe.playerThread.join();} catch (InterruptedException ignored) {}}
        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
            while (!terminate) {
                // TODO implement player key press simulator
                Random rand = new Random();
                int slot = -1;
                while ((slot == -1 || table.slotToCard[slot] == null) && !terminate){
                    slot = rand.nextInt(table.slotToCard.length);
                }
                synchronized (this) {
                    if((pressed.size() >= three) && !terminate) {
                        try {
                            wait();
                        } catch (InterruptedException ignored) {}
                    }
                    if(pressed.size() < three){
                        keyPressed(slot);
                        notifyAll();
                    }
                }
            }
            if(joinMe != null){
            if(joinMe.aiThread != null) try{ joinMe.aiThread.join(); } catch (InterruptedException ignored){}}
            System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        // TODO implement
        terminate = true;
        if(!human && aiThread != null)
            aiThread.interrupt();
    }

    /**
     * This method is called when a key is pressed. if the player presses a key that represent a slot that he already
     * put token on, it removes the token from his slot
     *
     * @param slot - the slot corresponding to the key pressed.
     */

    public void keyPressed(int slot) {
        if(!gotPoint && !gotPenalty) {
            pressed.add(slot);
            synchronized (waitForPress) {
                waitForPress.notifyAll();
            }
        }
    }

    public void keyPressed2(int slot) {
        // TODO implement
        if (tokens.contains(slot))
            removeToken(slot);
        else {
            if (tokens.size() < three) {
                if(table.slotToCard[slot] != null)
                    placeToken(slot);
                if(tokens.size() == three) {
                    shoutSet();
                }
            }
        }
    }


    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
        gotPoint = true;
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        setFreezeTime(env.config.penaltyFreezeMillis); // = env.config.penaltyFreezeMillis
        dealer.getTd().freezePlayer(this);
        try{
              Thread.sleep(env.config.penaltyFreezeMillis);
        }catch (InterruptedException ignored){}
        gotPenalty = false;
    }

    public void pointFreeze() {
        setFreezeTime(env.config.pointFreezeMillis); //env.config.pointFreezeMillis
        dealer.getTd().freezePlayer(this);
        try{
            Thread.sleep(env.config.pointFreezeMillis);
        }catch (InterruptedException ignored){}
        gotPoint = false;
    }

    public int score() {
        return score;
    }

    public void clearTokens(){
        synchronized (tokensLock) {
            tokens.clear();
        }
    }


    private void shoutSet() { // our func
        //TODO implement
        if(!dealer.isTerminated()) {
            synchronized (dealer) {
                dealer.addPlayerToQueue(this);
                if (dealer.isSleeping()) {
                    dealer.getDealerThread().interrupt();
                }
                if (!dealer.isTerminated()) {
                    try {
                        dealer.wait();
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }


    public LinkedList<Integer> getTokens() {
        synchronized (tokensLock) {
            return tokens;
        }
    }

    public void placeToken(int slot){
        synchronized (table) {
            synchronized (tokensLock) {
                table.placeToken(id, slot);
                tokens.add(slot);
            }
        }
    }

    public void removeToken(int slot){
        synchronized (table) {
            synchronized (tokensLock) {
                table.removeToken(id, slot);
                if(tokens.indexOf(slot) > -1)
                    tokens.remove(tokens.indexOf(slot));
            }
        }
    }

    public void givePenalty() {
        gotPenalty = true;
    }

    public long getFreezeTime(){
        return freezeTime;
    }

    public void setFreezeTime(long newTime){
        freezeTime = System.currentTimeMillis() + newTime;
    }


    public void setJoinMe(Player next){ joinMe = next; }

    public boolean hasPenalty(){ //for testing purpose only
        return gotPenalty;
    }

    public boolean isHuman(){
        return human;
    }

}
