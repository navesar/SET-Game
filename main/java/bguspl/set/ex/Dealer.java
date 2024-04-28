package bguspl.set.ex;

import bguspl.set.Env;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    private Thread dealerThread;

    private TimerDealer td;

    private volatile Queue<Player> playersQueue;

    private volatile boolean isSleeping;

    private static final int three = 3;

    private static final int zero = 0;

    private long elapsedTime;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        td = new TimerDealer(env, this);
        playersQueue = new LinkedList<>();
        isSleeping = false;
        elapsedTime = System.currentTimeMillis();
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        System.out.printf("Info: Thread %s starting.%n", Thread.currentThread().getName());
        boolean started = false;
        while (!shouldFinish()) {
            if(!started) {
                placeCardsOnTable();
                elapsedTime = System.currentTimeMillis();
                dealerThread = Thread.currentThread();
                td.setJoinMe(players[zero]);
                new Thread(td).start();
                started = true;
                for (int i = 0; i < players.length; i++) {
                    new Thread(players[i]).start();
//                    try{
//                        Thread.sleep(50);
//                    }catch (InterruptedException ignored){}
//                    System.out.println("startd "+players[i].playerThread.getName()+" for player "+players[i].id);
                    if(i < players.length -1)
                        players[i].setJoinMe(players[i+1]);
                }
            }
            timerLoop();
            removeAllCardsFromTable();
        }
        if(!terminate) {
            announceWinners();
            terminate();
        }

        System.out.printf("Info: Thread %s terminated.%n", Thread.currentThread().getName());
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public synchronized void terminate() {
        // TODO implement
        terminate = true;
        for(int i = players.length-1; i >=0 ; i--){
            players[i].terminate();
            if(players[i].isHuman() && players[i].playerThread != null) players[i].playerThread.interrupt();
        }
        notifyAll();
        td.terminate();
        if(isSleeping) dealerThread.interrupt();
        if (td.getTimerDealerThread() != null) {
            try {
                td.getTimerDealerThread().join();
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks if any cards should be removed from the table and returns them to the deck.
     */

    private synchronized void removeCardsFromTable() {
        // TODO implement
        synchronized (table) {
            while (!playersQueue.isEmpty()) {
                Player shoutedSet = playersQueue.remove();
                if(shoutedSet.getTokens().size() == three) {
                    int[] cards = new int[three];
                    LinkedList<Integer> tokens = shoutedSet.getTokens();
                    LinkedList<Integer> tokens2 = new LinkedList<>(tokens);
                    boolean cardsExist = true;
                    for (int i = 0; i < cards.length && cardsExist; i++) {
                        int slot = tokens.get(i);
                        if (table.slotToCard[slot] != null) {
                            cards[i] = table.slotToCard[slot];
                        } else
                            cardsExist = false;
                    }
                    if (cardsExist) {
                        if (env.util.testSet(cards)) {
                            LinkedList<Integer> clearTokensFromHere = null;
                            while (tokens.size() > zero) {//remove tokens from the slot
                                int slot = tokens.remove();
                                clearTokensFromHere = new LinkedList<>(table.slotToTokens[slot]);
                                for (int i = 0; i < clearTokensFromHere.size(); i++) {
                                    int playerId = clearTokensFromHere.get(i);
                                    Player player = findPlayer(playerId);
                                    player.removeToken(slot);
                                }
                            }
                            for (int i = 0; i < cards.length; i++) {
                                table.removeCard(tokens2.get(i));
                            }
                            shoutedSet.point();
                            placeCardsOnTable();
                            elapsedTime = System.currentTimeMillis();
                        } else {
                            shoutedSet.givePenalty();
                        }
                        td.getTimerDealerThread().interrupt();
                    }
                }
            }
        }
    }


    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        // TODO implement
        Random rand = new Random();
        int cardIndex = -1;
        if(!deck.isEmpty())
            cardIndex = rand.nextInt(deck.size());
        LinkedList<Integer> slots = new LinkedList<>();
        for(int i = 0; i < table.slotToCard.length; i++){
            if(table.slotToCard[i] == null)
                slots.add(i);
        }
        int slotIndex = -1;
        while(!deck.isEmpty() && !slots.isEmpty()) {
            slotIndex = rand.nextInt(slots.size());
            Integer card = deck.remove(cardIndex);
            if(!deck.isEmpty())
                cardIndex = rand.nextInt(deck.size());
            table.placeCard(card, slots.get(slotIndex));
            env.ui.placeCard(card, slots.get(slotIndex));
            slots.remove(slotIndex);
        }
        updateTimerDisplay(true);
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        synchronized(this){
            notifyAll();
        }
        if(playersQueue.isEmpty()) {
            try {
                isSleeping = true;
                if(reshuffleTime - System.currentTimeMillis() <= zero)
                    updateTimerDisplay(true);
                else
                    Thread.sleep(reshuffleTime - System.currentTimeMillis());
            } catch (InterruptedException ignored) {
                isSleeping = false;
            }
        }
        isSleeping = false;
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        if (reset) {
            td.resetTime();
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        synchronized (table) {
            env.ui.removeTokens();
            Random rand = new Random();
            LinkedList<Integer> slots = new LinkedList<>();
            for (int i = 0; i < table.slotToCard.length; i++) {
                slots.add(i);
            }
            while (!slots.isEmpty()) {
                int index = rand.nextInt(slots.size());
                Integer card = table.slotToCard[slots.get(index)];
                if (card != null)
                    deck.add(card);
                table.removeCard(slots.get(index));
                slots.remove(index);
            }
            //remove all tokens
            for (Player p : players)
                p.clearTokens();
            shuffle();
            if (!shouldFinish())
                placeCardsOnTable();
        }
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        Integer winnerScore = 0;
        int size = 0;
        for(Player p : players){
            Integer pScore = p.score();
            if(pScore > winnerScore) {
                winnerScore = pScore;
                size = 1;
            }
            else{
                if(pScore == winnerScore)
                    size++;
            }
        }
        int[] winners = new int[size];
        for(Player p : players){
            if(p.score() == winnerScore){
                winners[0] = p.id;
            }
        }
        env.ui.announceWinner(winners);
    }

    private void shuffle(){ //myFunc
        Collections.shuffle(deck);
    }

    private Player findPlayer(int playerId){
        for(Player p : players) {
            if(p.id == playerId)
                return p;
        }
        return null;
    }

    public Thread getDealerThread(){
        return dealerThread;
    }

    public TimerDealer getTd(){
        return td;
    }

    public synchronized void addPlayerToQueue(Player player){
        playersQueue.add(player);
    }

    public boolean isSleeping(){
        return isSleeping;
    }


    public boolean isTerminated(){
        return terminate;
    }

    public long getReshuffleTime(){ return reshuffleTime; }

    public long getElapsedTime(){ return elapsedTime;}

    public boolean isQueueEmpty(){ //for testing purpose only
        return playersQueue.isEmpty();
    }
}
