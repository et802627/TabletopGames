package games.diamant;

import core.AbstractGameState;
import core.AbstractGameStateWithTurnOrder;
import core.AbstractParameters;
import core.actions.AbstractAction;
import core.components.Component;
import core.components.Counter;
import core.components.Deck;
import core.interfaces.IPrintable;
import core.interfaces.IVectorisable;
import core.turnorders.AlternatingTurnOrder;
import core.turnorders.StandardTurnOrder;
import core.turnorders.TurnOrder;
import games.GameType;
import games.diamant.cards.DiamantCard;
import games.diamant.components.ActionsPlayed;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.stream.IntStream;


public class DiamantGameState extends AbstractGameState implements IPrintable, IVectorisable {
    Deck<DiamantCard>          mainDeck;
    Deck<DiamantCard>          discardDeck;
    Deck<DiamantCard>          path;

    List<Counter> treasureChests;
    List<Counter> hands;
    List<Boolean> playerInCave;

    @Override
    public String getObservationJson() {
        final JSONObject json = new JSONObject();
        json.put("cave", nCave);
        json.put("playersInCave", playerInCave.size());
        json.put("pathNoGems", nGemsOnPath);
        json.put("chestNoGems", getTreasureChests().get(getCurrentPlayer()).getValue());
        json.put("hazardScorpionsOnPath", nHazardExplosionsOnPath);
        json.put("hazardSnakesOnPath", nHazardSnakesOnPath);
        json.put("hazardRockfallsOnPath", nHazardRockfallsOnPath);
        json.put("hazardPoisonOnPath", nHazardPoissonGasOnPath);
        json.put("hazardExplosionsOnPath", nHazardExplosionsOnPath);
        return json.toJSONString();
    }

    @Override
    public double[] getObservationVector() {
        double[] retVal = new double[getObservationSpace()];
        retVal[0] = getTreasureChests().get(getCurrentPlayer()).getValue();
        retVal[1] = path.getComponents().get(path.getSize()-1).getNumberOfGems(); // nGemsOnPath;
        retVal[2] = playerInCave.size();
        retVal[3] = nCave;
        retVal[4] = nHazardExplosionsOnPath;
        retVal[5] = nHazardPoissonGasOnPath;
        retVal[6] = nHazardRockfallsOnPath;
        retVal[7] = nHazardScorpionsOnPath;
        retVal[8] = nHazardSnakesOnPath;
        return retVal;
    }

    @Override
    public double[] getNormalizedObservationVector() {
        double[] retVal = new double[getObservationSpace()];
        retVal[0] = getTreasureChests().get(getCurrentPlayer()).getValue() / 100d;
        retVal[1] = path.getComponents().get(path.getSize()-1).getNumberOfGems() / 17d;
        retVal[2] = playerInCave.size() / (double) getNPlayers();
        retVal[3] = nCave / 5d;
        retVal[4] = nHazardExplosionsOnPath / 3d;
        retVal[5] = nHazardPoissonGasOnPath/ 3d;
        retVal[6] = nHazardRockfallsOnPath /3d;
        retVal[7] = nHazardScorpionsOnPath /3d;
        retVal[8] = nHazardSnakesOnPath/ 3d;
        return retVal;
    }

    @Override
    public int getObservationSpace() {
        return 9;
    }

    // helper data class to store interesting information
    static class PlayerTurnRecord {
        public final int player;
        public final int round;
        public final int turnLeft;

        PlayerTurnRecord(int player, int round, int turn) {
            this.player = player;
            this.round = round;
            this.turnLeft = turn;
        }
    }

    List<PlayerTurnRecord> recordOfPlayerActions = new ArrayList<>();

    int nGemsOnPath             = 0;
    int nHazardPoissonGasOnPath = 0;
    int nHazardScorpionsOnPath  = 0;
    int nHazardSnakesOnPath     = 0;
    int nHazardRockfallsOnPath  = 0;
    int nHazardExplosionsOnPath = 0;

    int nCave = 0;

    // This component store the actions played for the rest of players.
    // It is needed since in this game, actions are simultaneously played
    ActionsPlayed actionsPlayed;

    /**
     * Constructor. Initialises some generic game state variables.
     *
     * @param gameParameters - game parameters.
     * @param nPlayers      - number of players for this game.
     */
    public DiamantGameState(AbstractParameters gameParameters, int nPlayers) {
        super(gameParameters, nPlayers);
    }

    @Override
    protected GameType _getGameType() {
        return GameType.Diamant;
    }

    @Override
    protected List<Component> _getAllComponents() {
        return new ArrayList<Component>() {{
            add(mainDeck);
            add(discardDeck);
            add(path);
            addAll(treasureChests);
            addAll(hands);
            add(actionsPlayed);
        }};
    }

    @Override
    protected AbstractGameState _copy(int playerId)
    {
        Random r = new Random(getGameParameters().getRandomSeed());

        DiamantGameState dgs = new DiamantGameState(gameParameters.copy(), getNPlayers());

        dgs.mainDeck    = mainDeck.copy();
        dgs.discardDeck = discardDeck.copy();
        dgs.path        = path.copy();
        dgs.actionsPlayed  = (ActionsPlayed) actionsPlayed.copy();

        dgs.nGemsOnPath             = nGemsOnPath;
        dgs.nHazardPoissonGasOnPath = nHazardPoissonGasOnPath;
        dgs.nHazardScorpionsOnPath  = nHazardScorpionsOnPath;
        dgs.nHazardSnakesOnPath     = nHazardSnakesOnPath;
        dgs.nHazardRockfallsOnPath  = nHazardRockfallsOnPath;
        dgs.nHazardExplosionsOnPath = nHazardExplosionsOnPath;

        dgs.nCave          = nCave;
        dgs.hands          = new ArrayList<>();
        dgs.treasureChests = new ArrayList<>();
        dgs.playerInCave   = new ArrayList<>();
        dgs.recordOfPlayerActions.addAll(recordOfPlayerActions);

        for (Counter c : hands)
            dgs.hands.add(c.copy());

        for (Counter c : treasureChests)
            dgs.treasureChests.add(c.copy());

        // If there is an action played for a player, then copy it
        for (int i=0; i<getNPlayers(); i++)
        {
            if (actionsPlayed.containsKey(i))
                dgs.actionsPlayed.put(i, actionsPlayed.get(i).copy());
        }

        dgs.playerInCave.addAll(playerInCave);

        // mainDeck and is actionsPlayed are hidden.
        if (getCoreGameParameters().partialObservable && playerId != -1)
        {
            dgs.mainDeck.shuffle(new Random(getGameParameters().getRandomSeed()));

            dgs.actionsPlayed.clear();

            // TODO: We also should remove the history entries for the removed actions
            // This is not formally necessary, as nothing currently uses this information, but in
            // a competition setting for example, it would be critical. There is no simple way to do this at the moment
            // because history (as part of the super-class) is only copied after we return from this _copy() method.

           // Randomize actions for other players (or any other modelling approach)
            // is now the responsibility of the deciding agent (see for example OSLA)

        }
        return dgs;
    }

    @Override
    protected double _getHeuristicScore(int playerId)
    {
        return new DiamantHeuristic().evaluateState(this, playerId);
    }
    /**
     * This provides the current score in game turns. This will only be relevant for games that have the concept
     * of victory points, etc.
     * If a game does not support this directly, then just return 0.0
     *
     * @param playerId
     * @return - double, score of current state
     */
    @Override
    public double getGameScore(int playerId) {
         return treasureChests.get(playerId).getValue();
    }

    @Override
    protected ArrayList<Integer> _getUnknownComponentsIds(int playerId)
    {
        ArrayList<Integer> ids = new ArrayList<>();
        ids.add(actionsPlayed.getComponentID());
        return ids;
    }

    protected void _reset() {
        mainDeck       = null;
        discardDeck    = null;
        path           = null;
        actionsPlayed  = null;
        treasureChests = new ArrayList<>();
        hands          = new ArrayList<>();
        playerInCave   = new ArrayList<>();

        nGemsOnPath             = 0;
        nHazardPoissonGasOnPath = 0;
        nHazardScorpionsOnPath  = 0;
        nHazardSnakesOnPath     = 0;
        nHazardRockfallsOnPath  = 0;
        nHazardExplosionsOnPath = 0;

        nCave = 0;

    }

    @Override
    protected boolean _equals(Object o)
    {
        if (this == o)                        return true;
        if (!(o instanceof DiamantGameState)) return false;
        if (!super.equals(o))                 return false;

        DiamantGameState that = (DiamantGameState) o;

        return nGemsOnPath             == that.nGemsOnPath             &&
               nHazardExplosionsOnPath == that.nHazardExplosionsOnPath &&
               nHazardPoissonGasOnPath == that.nHazardPoissonGasOnPath &&
               nHazardRockfallsOnPath  == that.nHazardRockfallsOnPath  &&
               nHazardScorpionsOnPath  == that.nHazardScorpionsOnPath  &&
               nHazardSnakesOnPath     == that.nHazardSnakesOnPath     &&
               nCave                   == that.nCave                   &&
               Objects.equals(mainDeck,       that.mainDeck)           &&
               Objects.equals(discardDeck,    that.discardDeck)        &&
               Objects.equals(hands,          that.hands)              &&
               Objects.equals(treasureChests, that.treasureChests)     &&
               Objects.equals(path,           that.path)               &&
               Objects.equals(playerInCave,   that.playerInCave)       &&
               Objects.equals(actionsPlayed,  that.actionsPlayed);
    }

    /**
     * Returns the number of player already in the cave
    */

    public int getNPlayersInCave()
    {
        int n = 0;
        for (Boolean b: playerInCave)
            if (b) n++;
        return n;
    }

    public List<PlayerTurnRecord> getRecordOfPlayerActions() {
        return recordOfPlayerActions;
    }

    @Override
    public void printToConsole() {
        String[] strings = new String[13];

        StringBuilder str_gemsOnHand          = new StringBuilder();
        StringBuilder str_gemsOnTreasureChest = new StringBuilder();
        StringBuilder str_playersOnCave       = new StringBuilder();

        for (Counter c:hands)          { str_gemsOnHand.         append(c.getValue()).append(" "); }
        for (Counter c:treasureChests) { str_gemsOnTreasureChest.append(c.getValue()).append(" "); }
        for (Boolean b : playerInCave)
        {
            if (b) str_playersOnCave.append("T");
            else   str_playersOnCave.append("F");
        }

        strings[0]  = "----------------------------------------------------";
        strings[1]  = "Cave:                       " + nCave;
        strings[2]  = "Players on Cave:            " + str_playersOnCave.toString();
        strings[3]  = "Path:                       " + path.toString();
        strings[4]  = "Gems on Path:               " + nGemsOnPath;
        strings[5]  = "Gems on hand:               " + str_gemsOnHand.toString();
        strings[6]  = "Gems on treasure chest:     " + str_gemsOnTreasureChest.toString();
        strings[7]  = "Hazard scorpions in Path:   " + nHazardScorpionsOnPath  + ", in Main deck: " + getNHazardCardsInMainDeck(DiamantCard.HazardType.Scorpions);
        strings[8]  = "Hazard snakes in Path:      " + nHazardSnakesOnPath     + ", in Main deck: " + getNHazardCardsInMainDeck(DiamantCard.HazardType.Snakes);
        strings[9]  = "Hazard rockfalls in Path:   " + nHazardRockfallsOnPath  + ", in Main deck: " + getNHazardCardsInMainDeck(DiamantCard.HazardType.Rockfalls);
        strings[10] = "Hazard poisson gas in Path: " + nHazardPoissonGasOnPath + ", in Main deck: " + getNHazardCardsInMainDeck(DiamantCard.HazardType.PoissonGas);
        strings[11] = "Hazard explosions in Path:  " + nHazardExplosionsOnPath + ", in Main deck: " + getNHazardCardsInMainDeck(DiamantCard.HazardType.Explosions);
        strings[12] = "----------------------------------------------------";

        for (String s : strings){
            System.out.println(s);
        }
    }

    private int getNHazardCardsInMainDeck(DiamantCard.HazardType ht)
    {
        int n = 0;
        for (int i=0; i<mainDeck.getSize(); i++)
        {
            if (mainDeck.get(i).getHazardType() == ht)
                n ++;
        }
        return n;
    }

    public Deck<DiamantCard> getMainDeck()       { return mainDeck;       }
    public Deck<DiamantCard> getDiscardDeck()    { return discardDeck;    }
    public List<Counter>     getHands()          { return hands;          }
    public List<Counter>     getTreasureChests() { return treasureChests; }
    public Deck<DiamantCard> getPath()           { return path;           }
    public ActionsPlayed     getActionsPlayed()  { return actionsPlayed;  }
    public void setActionPlayed(int player, AbstractAction action) {
        actionsPlayed.put(player, action);
    }
}
