package games.dicemonastery.test;


import core.actions.AbstractAction;
import core.actions.DoNothing;
import games.dicemonastery.*;
import games.dicemonastery.DiceMonasteryConstants.ActionArea;
import games.dicemonastery.DiceMonasteryConstants.Resource;
import games.dicemonastery.DiceMonasteryConstants.Season;
import games.dicemonastery.DiceMonasteryConstants.TREASURE;
import games.dicemonastery.actions.*;
import org.junit.Test;
import players.simple.RandomPlayer;

import java.util.*;

import static games.dicemonastery.DiceMonasteryConstants.ActionArea.*;
import static games.dicemonastery.DiceMonasteryConstants.BONUS_TOKEN.*;
import static games.dicemonastery.DiceMonasteryConstants.GOSPEL_REWARD;
import static games.dicemonastery.DiceMonasteryConstants.ILLUMINATED_TEXT.*;
import static games.dicemonastery.DiceMonasteryConstants.PSALM_REWARDS;
import static games.dicemonastery.DiceMonasteryConstants.Phase.PLACE_MONKS;
import static games.dicemonastery.DiceMonasteryConstants.Phase.USE_MONKS;
import static games.dicemonastery.DiceMonasteryConstants.Resource.*;
import static games.dicemonastery.DiceMonasteryConstants.Season.*;
import static games.dicemonastery.DiceMonasteryConstants.TREASURE.CAPE;
import static games.dicemonastery.DiceMonasteryConstants.TREASURE.ROBE;
import static java.util.stream.Collectors.*;
import static org.junit.Assert.*;

public class ActionTests {
    DiceMonasteryForwardModel fm = new DiceMonasteryForwardModel();
    DiceMonasteryGame game = new DiceMonasteryGame(fm, new DiceMonasteryGameState(new DiceMonasteryParams(3), 4));
    DiceMonasteryGameState state = (DiceMonasteryGameState) game.getGameState();
    DiceMonasteryTurnOrder turnOrder = (DiceMonasteryTurnOrder) game.getGameState().getTurnOrder();
    RandomPlayer rnd = new RandomPlayer();

    private void startOfUseMonkPhaseForArea(ActionArea region, Season season, Map<Integer, ActionArea> overrides) {
        do {
            // first take random action until we get to the point required
            while (state.getGamePhase() == USE_MONKS)
                fm.next(state, rnd.getAction(state, fm.computeAvailableActions(state)));

            // then place all monks randomly
            do {
                int player = state.getCurrentPlayer();
                List<AbstractAction> availableActions = fm.computeAvailableActions(state);
                AbstractAction chosen = rnd.getAction(state, availableActions);
                if (overrides.containsKey(player) && availableActions.contains(new PlaceMonk(player, overrides.get(player)))) {
                    chosen = new PlaceMonk(player, overrides.get(player));
                }
                fm.next(state, chosen);
            } while (state.getGamePhase() == PLACE_MONKS);

            // then act randomly until we get to the point required
            while (turnOrder.getCurrentArea() != region) {
                fm.next(state, rnd.getAction(state, fm.computeAvailableActions(state)));
            }
        } while (turnOrder.getSeason() != season);
    }

    private void startOfUseMonkPhaseForAreaAfterBonusToken(ActionArea region, Season season) {
        startOfUseMonkPhaseForAreaAfterBonusToken(region, season, new HashMap<>());
    }

    private void startOfUseMonkPhaseForAreaAfterBonusToken(ActionArea region, Season season, Map<Integer, ActionArea> overrides) {
        startOfUseMonkPhaseForArea(region, season, overrides);

        // finally we take BONUS_TOKEN and possible PROMOTION
        assertTrue(fm.computeAvailableActions(state).get(0) instanceof TakeToken);
        fm.next(state, fm.computeAvailableActions(state).get(0)); // take one of the tokens
        if (state.isActionInProgress())
            fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote a monk

        // then we decide not to Pray (if we have the option)
        if (fm.computeAvailableActions(state).stream().anyMatch(a -> a instanceof Pray))
            fm.next(state, new Pray(0)); // decline to Pray
    }

    @Test
    public void meadowActionsCorrectSpring() {
        startOfUseMonkPhaseForAreaAfterBonusToken(MEADOW, SPRING);
        int ap = turnOrder.getActionPointsLeft();

        int expectedActions = ap >= 2 ? 5 : 4;
        assertEquals(expectedActions, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new SowWheat()));
        assertTrue(fm.computeAvailableActions(state).contains(new Forage(1)));
        if (ap >= 5)
            assertTrue(fm.computeAvailableActions(state).contains(new Forage(5)));
        assertTrue(fm.computeAvailableActions(state).contains(new PlaceSkep()));
    }

    @Test
    public void noPrayerOpportunityIfNoPrayerTokens() {
        for (int p = 0; p < 4; p++)
            state.addResource(p, PRAYER, -1); // remove starting Prayers
        startOfUseMonkPhaseForArea(MEADOW, SPRING, Collections.emptyMap());
        // finally we take BONUS_TOKEN and possible PROMOTION
        state.putToken(MEADOW, PROMOTION, 0);
        state.putToken(MEADOW, PROMOTION, 1);
        assertTrue(fm.computeAvailableActions(state).get(0) instanceof TakeToken);
        fm.next(state, fm.computeAvailableActions(state).get(0)); // take one of the tokens
        if (state.isActionInProgress())
            fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote a monk

        int ap = turnOrder.getActionPointsLeft();

        // Now check we move straight on to actions
        assertEquals(0, state.getResource(state.getCurrentPlayer(), PRAYER, STOREROOM));
        assertEquals(ap >= 2 ? 5 : 4, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new SowWheat()));
        assertTrue(fm.computeAvailableActions(state).contains(new Forage(1)));
        if (ap >= 5)
            assertTrue(fm.computeAvailableActions(state).contains(new Forage(5)));
        assertTrue(fm.computeAvailableActions(state).contains(new PlaceSkep()));
    }

    @Test
    public void prayerOptionsIfWeHaveDevotionTokens() {
        startOfUseMonkPhaseForArea(MEADOW, SPRING, Collections.emptyMap());
        state.addResource(state.getCurrentPlayer(), PRAYER, 1); // add Prayer token

        // finally we take BONUS_TOKEN and possible PROMOTION
        state.putToken(MEADOW, PROMOTION, 0);
        state.putToken(MEADOW, PROMOTION, 1);
        assertTrue(fm.computeAvailableActions(state).get(0) instanceof TakeToken);
        fm.next(state, fm.computeAvailableActions(state).get(0)); // take one of the tokens
        if (state.isActionInProgress())
            fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote a monk

        // Now check that we have an option to Pray
        assertEquals(3, fm.computeAvailableActions(state).size());
        for (int i = 0; i <= 2; i++)
            assertTrue(fm.computeAvailableActions(state).contains(new Pray(i)));
    }


    @Test
    public void prayerOptionIsSkippedInTheChapel() {
        startOfUseMonkPhaseForArea(CHAPEL, SPRING, Collections.emptyMap());
        for (int p = 0; p < 4; p++)
            state.addResource(p, PRAYER, 1); // add Prayer token, as they might have ben used already

        for (int i = 0; i < 4; i++) {
            // finally we take BONUS_TOKEN and possible PROMOTION
            if (i < 2) {
                assertTrue(fm.computeAvailableActions(state).get(0) instanceof TakeToken);
                fm.next(state, fm.computeAvailableActions(state).get(0)); // take one of the tokens
                if (state.isActionInProgress())
                    fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote a monk
            }

            // Now check that we have no option to Pray
       //     System.out.println(fm.computeAvailableActions(state).stream().map(Objects::toString).collect(joining("\n")));
            assertTrue(fm.computeAvailableActions(state).stream().noneMatch(a -> a instanceof Pray));

            fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote all monks
        }
    }

    @Test
    public void pray() {
        startOfUseMonkPhaseForArea(MEADOW, SPRING, Collections.emptyMap());

        // finally we take BONUS_TOKEN and possible PROMOTION
        state.putToken(MEADOW, PROMOTION, 0);
        state.putToken(MEADOW, PROMOTION, 1);
        assertTrue(fm.computeAvailableActions(state).get(0) instanceof TakeToken);
        fm.next(state, fm.computeAvailableActions(state).get(0)); // take one of the tokens
        if (state.isActionInProgress())
            fm.next(state, fm.computeAvailableActions(state).get(0)); // and promote a monk
        state.addResource(state.getCurrentPlayer(), PRAYER, 1); // add Prayer token

        assertEquals(2, state.getResource(state.getCurrentPlayer(), PRAYER, STOREROOM));
        int startingAP = turnOrder.getActionPointsLeft();
        fm.next(state, new Pray(2));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), PRAYER, STOREROOM));
        assertEquals(4 + startingAP, turnOrder.getActionPointsLeft());
    }

    @Test
    public void meadowActionsCorrectAutumn() {
        startOfUseMonkPhaseForAreaAfterBonusToken(MEADOW, AUTUMN);
        while (state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW) > 0)
            state.moveCube(state.getCurrentPlayer(), GRAIN, MEADOW, SUPPLY);
        while (state.getResource(state.getCurrentPlayer(), SKEP, MEADOW) > 0)
            state.moveCube(state.getCurrentPlayer(), SKEP, MEADOW, SUPPLY);

        int ap = turnOrder.getActionPointsLeft();
        int grain = state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW);
        int skeps = state.getResource(state.getCurrentPlayer(), SKEP, MEADOW);

        int expectedActions = ap >= 5 ? 3 : 2;
        expectedActions += Math.min(grain, 2);
        expectedActions += Math.min(skeps, 2);

        assertEquals(expectedActions, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new Forage(1)));
        if (ap >= 5)
            assertTrue(fm.computeAvailableActions(state).contains(new Forage(5)));

        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, MEADOW);
        grain = state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW);
        skeps = state.getResource(state.getCurrentPlayer(), SKEP, MEADOW);
        int piety = state.getAPLeft();
        assertEquals(ap >= 5 ? 4 : 3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new Forage(1)));
        if (ap >= 5)
            assertTrue(fm.computeAvailableActions(state).contains(new Forage(5)));
        assertTrue(fm.computeAvailableActions(state).contains(new HarvestWheat(1)));
        if (grain > 1)
            assertTrue(fm.computeAvailableActions(state).contains(new HarvestWheat(Math.min(grain, piety))));

        state.moveCube(state.getCurrentPlayer(), SKEP, SUPPLY, MEADOW);
        assertEquals(ap >= 5 ? 5 : 4, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new Forage(1)));
        if (ap >= 5)
            assertTrue(fm.computeAvailableActions(state).contains(new Forage(5)));
        assertTrue(fm.computeAvailableActions(state).contains(new HarvestWheat(1)));
        assertTrue(fm.computeAvailableActions(state).contains(new CollectSkep(1)));
        if (skeps > 1)
            assertTrue(fm.computeAvailableActions(state).contains(new CollectSkep(Math.min(skeps, piety))));
    }

    @Test
    public void sowWheat() {
        assertEquals(0, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
        state.useAP(-1);
        (new SowWheat()).execute(state);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
    }

    @Test
    public void harvestGrain() {
        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, MEADOW);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
        state.useAP(-1);
        (new HarvestWheat(1)).execute(state);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(4, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
    }

    @Test
    public void harvestAllGrain() {
        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, MEADOW);
        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, MEADOW);
        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, MEADOW);
        assertEquals(3, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
        state.useAP(-3);
        (new HarvestWheat(3)).execute(state);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), GRAIN, MEADOW));
        assertEquals(8, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
    }


    @Test
    public void placeSkep() {
        assertEquals(0, state.getResource(state.getCurrentPlayer(), SKEP, MEADOW));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
        state.useAP(-1);
        (new PlaceSkep()).execute(state);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), SKEP, MEADOW));
        assertEquals(1, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
    }

    @Test
    public void collectSkep() {
        state.useAP(-4);
        (new PlaceSkep()).execute(state);
        (new PlaceSkep()).execute(state);
        assertEquals(2, state.getResource(state.getCurrentPlayer(), SKEP, MEADOW));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), HONEY, STOREROOM));
        assertEquals(2, state.getResource(state.getCurrentPlayer(), WAX, STOREROOM));
        (new CollectSkep(2)).execute(state);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), SKEP, MEADOW));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
        assertEquals(4, state.getResource(state.getCurrentPlayer(), HONEY, STOREROOM));
        assertEquals(4, state.getResource(state.getCurrentPlayer(), WAX, STOREROOM));
    }

    @Test
    public void forage() {
        state.useAP(-100);
        int player = state.getCurrentPlayer();
        assertEquals(0, state.getStores(player, r -> r.isPigment).size());
        for (int i = 0; i < 20; i++)
            (new Forage(5)).execute(state);
        assertEquals(3, state.getStores(player, r -> r.isPigment).size()); // all three basic types
        assertEquals(200.0 / 9.0, state.getResource(state.getCurrentPlayer(), PALE_BLUE_PIGMENT, STOREROOM), 10);
        assertEquals(200.0 / 9.0, state.getResource(state.getCurrentPlayer(), PALE_GREEN_PIGMENT, STOREROOM), 10);
        assertEquals(200.0 / 9.0, state.getResource(state.getCurrentPlayer(), PALE_RED_PIGMENT, STOREROOM), 10);
        assertEquals(100.0 / 6.0, state.getResource(state.getCurrentPlayer(), BERRIES, STOREROOM), 10);
    }

    @Test
    public void kitchenActionsCorrect() {
        startOfUseMonkPhaseForAreaAfterBonusToken(KITCHEN, SPRING);

        while (state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM) > 0)
            state.moveCube(state.getCurrentPlayer(), GRAIN, STOREROOM, SUPPLY);
        while (state.getResource(state.getCurrentPlayer(), HONEY, STOREROOM) > 0)
            state.moveCube(state.getCurrentPlayer(), HONEY, STOREROOM, SUPPLY);
        Set<Resource> allPigments = state.getStores(state.getCurrentPlayer(), r -> r.isPigment).keySet();
        for (Resource r : allPigments)
            state.addResource(state.getCurrentPlayer(), r, -state.getResource(state.getCurrentPlayer(), r, STOREROOM));
        assertEquals(1, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));

        state.useAP(turnOrder.getActionPointsLeft() - 1);
        state.moveCube(state.getCurrentPlayer(), GRAIN, SUPPLY, STOREROOM);
        assertEquals(2, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new BakeBread()));

        state.useAP(-1);
        assertEquals(3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new BakeBread()));
        assertTrue(fm.computeAvailableActions(state).contains(new BrewBeer()));

        state.moveCube(state.getCurrentPlayer(), HONEY, SUPPLY, STOREROOM);
        assertEquals(4, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new BrewMead()));

        state.moveCube(state.getCurrentPlayer(), PALE_BLUE_PIGMENT, SUPPLY, STOREROOM);
        assertEquals(5, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new PrepareInk(PALE_BLUE_PIGMENT)));

        state.moveCube(state.getCurrentPlayer(), PALE_RED_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), PALE_BLUE_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), PALE_GREEN_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), VIVID_BLUE_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), VIVID_GREEN_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), VIVID_PURPLE_PIGMENT, SUPPLY, STOREROOM);
        assertEquals(7, fm.computeAvailableActions(state).size());

        state.useAP(1);
        assertEquals(2, fm.computeAvailableActions(state).size());
    }

    @Test
    public void promotingAMonkViaABonusIncreasesAP() {
        DiceMonasteryGameState state = (DiceMonasteryGameState) game.getGameState();
        DiceMonasteryTurnOrder turnOrder = (DiceMonasteryTurnOrder) state.getTurnOrder();

        state.putToken(MEADOW, PROMOTION, 0);
        startOfUseMonkPhaseForArea(MEADOW, SPRING, Collections.emptyMap());
        int startingPiety = state.monksIn(MEADOW, state.getCurrentPlayer()).stream().mapToInt(Monk::getPiety).sum();
        fm.next(state, new TakeToken(PROMOTION, MEADOW, state.getCurrentPlayer()));
        fm.next(state, fm.computeAvailableActions(state).get(0)); // promote a random monk

        assertEquals(1 + startingPiety, state.monksIn(MEADOW, state.getCurrentPlayer()).stream().mapToInt(Monk::getPiety).sum());
        assertEquals(1 + startingPiety, turnOrder.getActionPointsLeft());
    }

    @Test
    public void retiringAMonkViaABonusStillGivesYouTheirActionPoints() {
        DiceMonasteryGameState state = (DiceMonasteryGameState) game.getGameState();
        DiceMonasteryTurnOrder turnOrder = (DiceMonasteryTurnOrder) state.getTurnOrder();

        state.putToken(MEADOW, PROMOTION, 0);
        Monk newMonk = state.createMonk(6, 0);
        state.moveMonk(newMonk.getComponentID(), DORMITORY, MEADOW);

        Map<Integer, ActionArea> override = new HashMap<>();
        override.put(0, MEADOW);
        startOfUseMonkPhaseForArea(MEADOW, SPRING, override);
        int startingPiety = state.monksIn(MEADOW, state.getCurrentPlayer()).stream().mapToInt(Monk::getPiety).sum();
        fm.next(state, new TakeToken(PROMOTION, MEADOW, state.getCurrentPlayer()));
        fm.next(state, new PromoteMonk(6, MEADOW)); // promote the 6er

        assertEquals(startingPiety - 6, state.monksIn(MEADOW, state.getCurrentPlayer()).stream().mapToInt(Monk::getPiety).sum());
        assertEquals( startingPiety, turnOrder.getActionPointsLeft());
    }

    @Test
    public void bakeBread() {
        state.useAP(-1);
        // Has 2 Grain in STOREROOM at setup
        (new BakeBread()).execute(state);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
        assertEquals(4, state.getResource(state.getCurrentPlayer(), BREAD, STOREROOM));
    }

    @Test
    public void prepareInk() {
        state.useAP(-1);
        try {
            (new PrepareInk(PALE_GREEN_PIGMENT)).execute(state);
            fail("Should throw exception");
        } catch (IllegalArgumentException error) {
           // expected!
        }
        state.useAP(-1);
        state.moveCube(state.getCurrentPlayer(), PALE_GREEN_PIGMENT, SUPPLY, STOREROOM);
        (new PrepareInk(PALE_GREEN_PIGMENT)).execute(state);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), PALE_GREEN_PIGMENT, STOREROOM));
        assertEquals(1, state.getResource(state.getCurrentPlayer(), PALE_GREEN_INK, STOREROOM));
    }

    @Test
    public void brewBeer() {
        state.useAP(-2);
        // Has 2 Grain in STOREROOM at setup
        (new BrewBeer()).execute(state);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), GRAIN, STOREROOM));
        assertEquals(1, state.getResource(state.getCurrentPlayer(), PROTO_BEER_1, STOREROOM));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), PROTO_BEER_2, STOREROOM));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), BEER, STOREROOM));

    }

    @Test
    public void brewMead() {
        state.useAP(-2);
        // Has 2 Honey in STOREROOM at setup
        (new BrewMead()).execute(state);
        assertEquals(1, state.getResource(state.getCurrentPlayer(), HONEY, STOREROOM));
        assertEquals(1, state.getResource(state.getCurrentPlayer(), PROTO_MEAD_1, STOREROOM));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), PROTO_MEAD_2, STOREROOM));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), MEAD, STOREROOM));
    }


    @Test
    public void workshopActionsCorrect() {
        startOfUseMonkPhaseForAreaAfterBonusToken(WORKSHOP, SPRING);

        state.useAP(turnOrder.getActionPointsLeft() - 1);

        Set<Resource> allPigments = state.getStores(state.getCurrentPlayer(), r -> r.isPigment).keySet();
        for (Resource r : allPigments)
            state.addResource(state.getCurrentPlayer(), r, -state.getResource(state.getCurrentPlayer(), r, STOREROOM));
        while (state.getResource(state.getCurrentPlayer(), WAX, STOREROOM) > 0)
            state.moveCube(state.getCurrentPlayer(), WAX, STOREROOM, SUPPLY);
        assertEquals(2, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new WeaveSkep()));

        state.moveCube(state.getCurrentPlayer(), PALE_RED_PIGMENT, SUPPLY, STOREROOM);
        assertEquals(2, fm.computeAvailableActions(state).size());

        state.useAP(-1);
        assertEquals(2, fm.computeAvailableActions(state).size());

        state.moveCube(state.getCurrentPlayer(), VIVID_BLUE_PIGMENT, SUPPLY, STOREROOM);
        assertEquals(3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new PrepareInk(VIVID_BLUE_PIGMENT)));

        state.moveCube(state.getCurrentPlayer(), WAX, SUPPLY, STOREROOM);
        assertEquals(4, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new MakeCandle()));

        state.moveCube(state.getCurrentPlayer(), CALF_SKIN, SUPPLY, STOREROOM);
        assertEquals(5, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new PrepareVellum()));

        state.moveCube(state.getCurrentPlayer(), VIVID_RED_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), VIVID_PURPLE_PIGMENT, SUPPLY, STOREROOM);
        state.moveCube(state.getCurrentPlayer(), VIVID_GREEN_PIGMENT, SUPPLY, STOREROOM);
        assertEquals(8, fm.computeAvailableActions(state).size());

        state.useAP(1);
        assertEquals(2, fm.computeAvailableActions(state).size());
    }

    @Test
    public void weaveSkep() {
        state.useAP(-1);
        assertEquals(2, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
        (new WeaveSkep()).execute(state);
        assertEquals(3, state.getResource(state.getCurrentPlayer(), SKEP, STOREROOM));
    }

    @Test
    public void prepareVellum() {
        state.useAP(-2);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), CALF_SKIN, STOREROOM));
        assertEquals(0, state.getResource(state.getCurrentPlayer(), VELLUM, STOREROOM));
        try {
            (new PrepareVellum()).execute(state);
            fail("Should not succeed");
        } catch (IllegalArgumentException e) {
            // expected
        }
        state.useAP(-2);
        state.addResource(state.getCurrentPlayer(), CALF_SKIN, 1);
        (new PrepareVellum()).execute(state);
        assertEquals(0, state.getResource(state.getCurrentPlayer(), CALF_SKIN, STOREROOM));
        assertEquals(1, state.getResource(state.getCurrentPlayer(), VELLUM, STOREROOM));
    }

    @Test
    public void makeCandle() {
        int player = state.getCurrentPlayer();
        state.useAP(-1);
        assertEquals(2, state.getResource(player, WAX, STOREROOM));
        assertEquals(0, state.getResource(player, CANDLE, STOREROOM));
        try {
            (new MakeCandle()).execute(state);
            fail("Should not succeed");
        } catch (IllegalArgumentException e) {
            // expected
        }
        assertEquals(1, turnOrder.getActionPointsLeft());
        state.useAP(-1);
        fm.next(state, (new MakeCandle()));
        assertEquals(1, state.getResource(player, WAX, STOREROOM));
        assertEquals(1, state.getResource(player, CANDLE, STOREROOM));
    }

    @Test
    public void gatehouseActionsCorrectWithoutPilgrimages() {
        startOfUseMonkPhaseForAreaAfterBonusToken(GATEHOUSE, SPRING);

        state.useAP(turnOrder.getActionPointsLeft() - 1);
        state.monksIn(GATEHOUSE, -1).stream()  // Move monks eligible for pilgrimage out of Gatehouse
                .filter(m -> m.getPiety() >=3 )
                .forEach( m-> state.moveMonk(m.getComponentID(), GATEHOUSE, DORMITORY));
        Monk p1 = state.createMonk(1, state.getCurrentPlayer());
        state.moveMonk(p1.getComponentID(), DORMITORY, GATEHOUSE);

        assertEquals(3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Pass()));
        assertTrue(fm.computeAvailableActions(state).contains(new BegForAlms(1)));
        assertTrue(fm.computeAvailableActions(state).contains(new VisitMarket()));

        state.useAP(-1);
        state.addResource(state.getCurrentPlayer(), SHILLINGS, 6 - state.getResource(state.getCurrentPlayer(), SHILLINGS, STOREROOM));
        assertEquals(5, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new BuyTreasure(TREASURE.CAPE)));
        assertTrue(fm.computeAvailableActions(state).contains(new BegForAlms(2)));

        state.acquireTreasure(TREASURE.CAPE, state.getCurrentPlayer());
        assertEquals(4, fm.computeAvailableActions(state).size());
        assertFalse(fm.computeAvailableActions(state).contains(new BuyTreasure(TREASURE.CAPE)));

        state.addResource(state.getCurrentPlayer(), SHILLINGS, 8);
        assertEquals(6, fm.computeAvailableActions(state).size());  // Two more Treasures in price range

        state.useAP(-1);
        assertEquals(7, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new HireNovice()));
    }

    @Test
    public void gatehousePilgrimageActionsCorrect() {
        startOfUseMonkPhaseForAreaAfterBonusToken(GATEHOUSE, SPRING);
        int player = state.getCurrentPlayer();

        state.monksIn(GATEHOUSE, -1).stream()  // Move monks eligible for pilgrimage out of Gatehouse
                .filter(m -> m.getPiety() >=3 )
                .forEach( m-> state.moveMonk(m.getComponentID(), GATEHOUSE, DORMITORY));

        // set AP to three, and remove all money
        state.useAP(turnOrder.getActionPointsLeft() - 3);
        state.addResource(player, SHILLINGS, -state.getResource(player, SHILLINGS, STOREROOM));

        assertTrue(fm.computeAvailableActions(state).stream().noneMatch(a -> a instanceof GoOnPilgrimage));

        Monk p4 = state.createMonk(4, player);
        state.moveMonk(p4.getComponentID(), DORMITORY, GATEHOUSE);
        state.addActionPoints(4);
        assertTrue(fm.computeAvailableActions(state).stream().noneMatch(a -> a instanceof GoOnPilgrimage));

        state.addResource(player, SHILLINGS, 3);
        assertEquals(1, fm.computeAvailableActions(state).stream().filter(a -> a instanceof GoOnPilgrimage).count());
        // ROME or SANTIAGO
        assertEquals(1, fm.computeAvailableActions(state).stream().filter(a -> a instanceof GoOnPilgrimage && ((GoOnPilgrimage) a).getActionPoints() == 4).count());

        state.addResource(player, SHILLINGS, 3); // can now go to JERUSALEM or ALEXANDRIA (but piety limits)
        assertEquals(1, fm.computeAvailableActions(state).stream().filter(a -> a instanceof GoOnPilgrimage).count());

        Monk p5 = state.createMonk(5, player);
        state.moveMonk(p5.getComponentID(), DORMITORY, GATEHOUSE);
        state.addActionPoints(5);
        assertEquals(3, fm.computeAvailableActions(state).stream().filter(a -> a instanceof GoOnPilgrimage).count());
        // Either can go on a short pilgrimage, but only 1 on a long one
        assertEquals(2, fm.computeAvailableActions(state).stream()
                .filter(a -> a instanceof GoOnPilgrimage && !((GoOnPilgrimage) a).destination.isLong()).count());
    }

    @Test
    public void pilgrimage() {
        startOfUseMonkPhaseForAreaAfterBonusToken(GATEHOUSE, SPRING);
        int player = state.getCurrentPlayer();
        Monk p5 = state.createMonk(5, player); // should be only piety 5 monk in Gatehouse
        state.moveMonk(p5.getComponentID(), DORMITORY, GATEHOUSE);
        state.addActionPoints(5);
        int ap = state.getAPLeft();
        assertEquals(8, state.pilgrimagesLeft(false));
        assertEquals(8, state.pilgrimagesLeft(true));
        assertEquals(0, state.getPilgrimagesStarted().size());
        Pilgrimage next = state.peekAtNextShortPilgrimage();

        fm.next(state, new GoOnPilgrimage(next.destination, 5));

        assertEquals(7, state.pilgrimagesLeft(false));
        assertEquals(8, state.pilgrimagesLeft(true));
        assertNotSame(next, state.peekAtNextShortPilgrimage());
        assertEquals(1, state.getPilgrimagesStarted().size());
        assertEquals(ap - 5, state.getAPLeft()); // uses all AP
    }

    @Test
    public void cannotChoosePilgrimageIfNoneLeft() {
        startOfUseMonkPhaseForAreaAfterBonusToken(GATEHOUSE, SPRING);
        int player = state.getCurrentPlayer();

        state.addResource(player, SHILLINGS, 10);
        Monk p5 = state.createMonk(5, player); // should be only piety 5 monk in Gatehouse
        state.moveMonk(p5.getComponentID(), DORMITORY, GATEHOUSE);
        state.addActionPoints(5);

        for (int i = 0; i < 8; i++) {
            assertTrue(fm.computeAvailableActions(state).stream()
                    .anyMatch(a -> a instanceof GoOnPilgrimage && !((GoOnPilgrimage) a).destination.isLong()));
            Monk pilgrim = state.createMonk(3, 0);
            state.moveMonk(pilgrim.getComponentID(), DORMITORY, GATEHOUSE);
            state.addResource(0, SHILLINGS, 3);
            state.startPilgrimage(state.peekAtNextShortPilgrimage().destination, pilgrim);
        }

        assertTrue(fm.computeAvailableActions(state).stream()
                .noneMatch(a -> a instanceof GoOnPilgrimage && !((GoOnPilgrimage) a).destination.isLong()));
    }

    @Test
    public void buyTreasure() {
        int player = state.getCurrentPlayer();
        state.addActionPoints(3);
        state.addResource(player, SHILLINGS, 4);

        fm.next(state, new BuyTreasure(ROBE));
        assertEquals(1, turnOrder.getActionPointsLeft());
        assertEquals(2, state.getResource(player, SHILLINGS, STOREROOM));
        assertEquals(2, state.getVictoryPoints(player));
        assertEquals(1, state.getNumberCommissioned(ROBE));
        assertEquals(0, state.getNumberCommissioned(CAPE));
    }

    @Test
    public void begForAlms() {
        int player = state.getCurrentPlayer();
        state.useAP(-2);
        assertEquals(6, state.getResource(player, SHILLINGS, STOREROOM));
        fm.next(state, (new BegForAlms(2)));
        assertEquals(8, state.getResource(player, SHILLINGS, STOREROOM));
        assertEquals(0, turnOrder.getActionPointsLeft());
    }

    @Test
    public void visitMarketToBuy() {
        state.addResource(state.getCurrentPlayer(), BREAD, -2);
        state.addResource(state.getCurrentPlayer(), SHILLINGS, -3); // should leave 3 over
        VisitMarket visit = new VisitMarket();
        MarketCard market = state.getCurrentMarket();

        visit._execute(state);
        assertEquals(visit, state.currentActionInProgress());
        assertEquals(2, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Buy(GRAIN, market.grain)));
        assertTrue(fm.computeAvailableActions(state).contains(new Buy(CALF_SKIN, market.calf_skin)));

        int player = state.getCurrentPlayer();
        state.addResource(state.getCurrentPlayer(), SHILLINGS, 3);
        if (market.pigmentType != null) {
            assertTrue(fm.computeAvailableActions(state).contains(new Buy(market.pigmentType, market.pigmentPrice)));
            assertEquals(3, fm.computeAvailableActions(state).size());
        }
        fm.next(state, (new Buy(CALF_SKIN, market.calf_skin)));
        assertTrue(visit.executionComplete(state));
        assertEquals(1, state.getResource(player, CALF_SKIN, STOREROOM));
        assertEquals(6 - market.calf_skin, state.getResource(player, SHILLINGS, STOREROOM));
        assertFalse(state.isActionInProgress());
    }

    @Test
    public void visitMarketToSell() {
        state.addResource(state.getCurrentPlayer(), SHILLINGS, -5); // 1 left - not enough to buy anything
        state.useAP(-1);
        VisitMarket visit = new VisitMarket();
        MarketCard market = state.getCurrentMarket();
        int player = state.getCurrentPlayer();
        fm.next(state, visit);
        assertEquals(1, fm.computeAvailableActions(state).size());
        assertEquals(new DoNothing(), fm.computeAvailableActions(state).get(0));
        assertEquals(player, state.getCurrentPlayer());
        state.addResource(player, BEER, 1);
        state.addResource(player, MEAD, 1);
        assertEquals(2, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Sell(BEER, market.beer)));
        assertTrue(fm.computeAvailableActions(state).contains(new Sell(MEAD, market.mead)));
        Sell action = (Sell) fm.computeAvailableActions(state).get(1);

        state.addResource(player, CANDLE, 1);
        assertEquals(3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new Sell(CANDLE, market.candle)));

        fm.next(state, action);
        assertTrue(visit.executionComplete(state));
        assertEquals(0, state.getResource(player, action.resource, STOREROOM));
        assertEquals(1 + action.price, state.getResource(player, SHILLINGS, STOREROOM));
        assertFalse(state.isActionInProgress());
    }

    @Test
    public void visitMarketToDoNothing() {
        state.addResource(state.getCurrentPlayer(), SHILLINGS, -5); // 1 left - not enough to buy anything
        state.addResource(state.getCurrentPlayer(), BREAD, -2);
        state.useAP(-1);

        VisitMarket visit = new VisitMarket();
        fm.next(state, visit);
        assertEquals(1, fm.computeAvailableActions(state).size());
        assertEquals(new DoNothing(), fm.computeAvailableActions(state).get(0));

        fm.next(state, fm.computeAvailableActions(state).get(0));
        assertTrue(visit.executionComplete(state));
        assertFalse(state.isActionInProgress());
    }

    @Test
    public void hireNovice() {
        state.useAP(-1);
        HireNovice action = new HireNovice();
        try {
            fm.next(state, action);
            fail("Should throw exception as not enough AP");
        } catch (IllegalArgumentException e) {
            // expected
        }
        state.useAP(-2);
        fm.next(state, action);
        assertEquals(7, state.monksIn(null, 0).size());
        assertEquals(7, state.monksIn(DORMITORY, 0).size());
        assertEquals(3, state.monksIn(DORMITORY, 0).stream().filter(m -> m.getPiety() == 1).count());
        assertEquals(0, state.getResource(0, SHILLINGS, STOREROOM));
        assertEquals(0, turnOrder.getActionPointsLeft());
    }

    @Test
    public void libraryActionsCorrect() {
        startOfUseMonkPhaseForAreaAfterBonusToken(LIBRARY, SPRING);

        state.useAP(turnOrder.getActionPointsLeft() - 1);
        assertEquals(1, turnOrder.getActionPointsLeft());
        int player = state.getCurrentPlayer();
        assertEquals(1, fm.computeAvailableActions(state).size());
        assertEquals(new Pass(), fm.computeAvailableActions(state).get(0));

        // first we clear out any items the player may have acquired to date
        state.addResource(player, VELLUM, -state.getResource(player, VELLUM, STOREROOM));
        state.addResource(player, CANDLE, -state.getResource(player, VELLUM, STOREROOM));
        for (Resource ink : Resource.values()) {
            if (ink.isInk)
                state.addResource(player, ink, -state.getResource(player, ink, STOREROOM));
        }

        state.useAP( -4);
        assertEquals(5, turnOrder.getActionPointsLeft());
        assertEquals(1, fm.computeAvailableActions(state).size());

        state.addResource(player, VELLUM, 2);
        state.addResource(player, CANDLE, 2);
        state.addResource(player, PALE_RED_INK, 3);

        assertEquals(2, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new WriteText(PSALM)));

        state.addResource(player, VIVID_RED_INK, 1);
        assertEquals(3, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new WriteText(EPISTLE)));

        state.addResource(player, PALE_GREEN_INK, 1);
        assertEquals(4, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new WriteText(LITURGY)));

        state.addResource(player, VIVID_BLUE_INK, 2);
        assertEquals(4, fm.computeAvailableActions(state).size());

        state.useAP( -1);
        assertEquals(5, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new WriteText(GOSPEL_LUKE)));
    }

    @Test
    public void chapelActionsCorrect() {
        startOfUseMonkPhaseForAreaAfterBonusToken(CHAPEL, SPRING);

        Set<Integer> pietyOfMonks = state.monksIn(CHAPEL, state.getCurrentPlayer()).stream()
                .map(Monk::getPiety)
                .collect(toSet());

        assertTrue(pietyOfMonks.size() > 0);
        int player = state.getCurrentPlayer();

        // NEW RULE (v1.8) with all monks auto-promoted
        Map<Integer, Integer> startingPieties = state.monksIn(CHAPEL, player).stream()
                .collect(toMap(Monk::getComponentID, Monk::getPiety));
        assertEquals(1, fm.computeAvailableActions(state).size());
        assertTrue(fm.computeAvailableActions(state).contains(new PromoteAllMonks(CHAPEL)));
        fm.next(state, fm.computeAvailableActions(state).get(0)); // Promote All Monks
        List<Monk> allMonks = state.monksIn(null, player);
        for (int id : startingPieties.keySet()) {
            if (startingPieties.get(id) == 6) {
                // retired
                assertTrue(state.monksIn(RETIRED, player).stream().anyMatch(m -> m.getComponentID() == id));
            } else {
                assertEquals(1 + startingPieties.get(id),
                        allMonks.stream().filter(m -> m.getComponentID() == id).collect(toList()).get(0).getPiety());
            }
        }
    }

    @Test
    public void promoteMonk() {
        startOfUseMonkPhaseForAreaAfterBonusToken(CHAPEL, SPRING);
        int player = state.getCurrentPlayer();

        int startingTotalPiety = state.monksIn(null, player).stream().mapToInt(Monk::getPiety).sum();
        List<Integer> pietyOfMonks = state.monksIn(CHAPEL, player).stream()
                .map(Monk::getPiety)
                .collect(toList());
        PromoteMonk promotion = new PromoteMonk(pietyOfMonks.get(0), CHAPEL);

        fm.next(state, promotion);
        assertEquals(1 + startingTotalPiety,
                state.monksIn(null, player).stream().mapToInt(Monk::getPiety).sum());

        fm.next(state, new Pass());
    }


    @Test
    public void bonusTokensGotToFirstTwoPlayersOnly() {
        Map<Integer, ActionArea> override = new HashMap<>();
        override.put(2, KITCHEN);
        override.put(0, KITCHEN);
        // players 0 and 2 will put all their monks in the Kitchen, and hence will go 1st and 2nd respectively (0 is abbot)
        // Their first actions will be to TakeToken - but not for players 1 and 3
        startOfUseMonkPhaseForArea(KITCHEN, SPRING, override);
        assertEquals(0, state.getCurrentPlayer());

        int player = -1;
        while (turnOrder.getCurrentArea() == KITCHEN) {
            List<AbstractAction> availableActions = fm.computeAvailableActions(state);
            if (state.getCurrentPlayer() != player) {
                player = state.getCurrentPlayer();
                // Check first action is to take a token (or not)
                if (player == 0 || player == 2) {
                    assertTrue(availableActions.stream().allMatch(a -> a instanceof TakeToken));
                } else if (turnOrder.getCurrentArea() == KITCHEN) {
                    assertTrue(availableActions.stream().noneMatch(a -> a instanceof TakeToken));
                }
            } else {
                assertTrue(availableActions.stream().noneMatch(a -> a instanceof TakeToken));
            }
            // and take action at random
            fm.next(state, rnd.getAction(state, availableActions));
        }
    }

    @Test
    public void retiringMonksGivesVPsAndRemovesThemFromGame() {
        startOfUseMonkPhaseForAreaAfterBonusToken(KITCHEN, AUTUMN);

        int startingVP = state.getVictoryPoints(1);
        int startingMonks = state.monksIn(null, 1).size();
        List<Monk> monks = state.monksIn(null, 1);
        int retiredMonks = state.monksIn(RETIRED, -1).size();
        int expectedReward = DiceMonasteryConstants.RETIREMENT_REWARDS[retiredMonks];
        promoteToRetirement(monks.get(0));
        assertEquals(startingVP + expectedReward, state.getVictoryPoints(1));
        assertEquals(startingMonks - 1, state.monksIn(null, 1).size());

        int nextExpectedReward = DiceMonasteryConstants.RETIREMENT_REWARDS[retiredMonks + 1];
        promoteToRetirement(monks.get(1));
        assertEquals(startingVP + expectedReward + nextExpectedReward, state.getVictoryPoints(1));
        assertEquals(startingMonks - 2, state.monksIn(null, 1).size());
    }

    private void promoteToRetirement(Monk m) {
        do {
            m.promote(state);
        } while (m.getPiety() < 6);
        m.promote(state);
    }

    @Test
    public void endOfYearPromotion() {
        turnOrder.setAbbot(1);
        do {
            fm.next(state, rnd.getAction(state, fm.computeAvailableActions(state)));
        } while (turnOrder.getSeason() != WINTER);

        // We have now moved to Winter
        assertEquals(1, state.getCurrentPlayer());
        List<AbstractAction> actions = fm.computeAvailableActions(state);
        assertTrue(actions.stream().allMatch(a -> a instanceof PromoteMonk));
        Set<Integer> pietyLevels = state.monksIn(null, 1).stream().mapToInt(Monk::getPiety).boxed().collect(toSet());
        assertEquals(pietyLevels.size(), actions.size());
    }

    @Test
    public void takeToken() {
        assertEquals(0, state.getCurrentPlayer());
        state.putToken(DORMITORY, DONATION, 0);
        assertEquals(6, state.getResource(1, SHILLINGS, STOREROOM));
        fm.next(state, new TakeToken(DONATION, DORMITORY, 1));
        assertEquals(8, state.getResource(1, SHILLINGS, STOREROOM));

        assertEquals(1, state.getCurrentPlayer());
        state.putToken(DORMITORY, DEVOTION, 0);
        assertEquals(1, state.getResource(1, PRAYER, STOREROOM));
        fm.next(state, new TakeToken(DEVOTION, DORMITORY, 1));
        assertEquals(2, state.getResource(1, PRAYER, STOREROOM));

        assertEquals(2, state.getCurrentPlayer());
        state.putToken(DORMITORY, PRESTIGE, 0);
        assertEquals(0, state.getVictoryPoints(2));
        fm.next(state, new TakeToken(PRESTIGE, DORMITORY, 2));
        assertEquals(1, state.getVictoryPoints(2));

        assertEquals(3, state.getCurrentPlayer());
        state.putToken(DORMITORY, PROMOTION, 0);
        assertEquals(13, state.monksIn(DORMITORY, 3).stream().mapToInt(Monk::getPiety).sum());
        fm.next(state, new TakeToken(PROMOTION, DORMITORY, 3));
        assertEquals(13, state.monksIn(DORMITORY, 3).stream().mapToInt(Monk::getPiety).sum());
        assertTrue(state.isActionInProgress());
        fm.next(state, new PromoteMonk(1, DORMITORY));
        assertFalse(state.isActionInProgress());
        assertEquals(14, state.monksIn(DORMITORY, 3).stream().mapToInt(Monk::getPiety).sum());
    }

    @Test
    public void writePsalm() {
        state.addActionPoints(10);
        WriteText action = new WriteText(PSALM);
        try {
            fm.next(state, action);
            fail("Should throw exception as not enough materials");
        } catch (IllegalArgumentException e) {
            // expected
        }

        int player = state.getCurrentPlayer();
        state.addResource(player, VELLUM, 2);
        state.addResource(player, CANDLE, 2);
        state.addResource(player, PALE_RED_INK, 2);

        assertEquals(0, state.getNumberWritten(PSALM));
        fm.next(state, action);
        assertEquals(1, state.getResource(player, VELLUM, STOREROOM));
        assertEquals(1, state.getResource(player, CANDLE, STOREROOM));
        assertEquals(1, state.getResource(player, PALE_RED_INK, STOREROOM));
        assertEquals(PSALM_REWARDS[0], state.getVictoryPoints(player));
        assertEquals(1, state.getNumberWritten(PSALM));
    }

    @Test
    public void writeGospel() {
        state.addActionPoints(10);
        WriteText action = new WriteText(GOSPEL_MATHEW);

        int player = state.getCurrentPlayer();
        state.addResource(player, VELLUM, 2);
        state.addResource(player, CANDLE, 2);
        state.addResource(player, PALE_RED_INK, 2);

        assertFalse(WriteText.meetsRequirements(GOSPEL_MATHEW, state.getStores(player, r -> true)));
        state.addResource(player, VIVID_PURPLE_INK, 1);
        state.addResource(player, VIVID_GREEN_INK, 1);
        assertFalse(WriteText.meetsRequirements(GOSPEL_MATHEW, state.getStores(player, r -> true)));
        state.addResource(player, VIVID_PURPLE_INK, 1);
        assertTrue(WriteText.meetsRequirements(GOSPEL_MATHEW, state.getStores(player, r -> true)));

        assertEquals(0, state.getNumberWritten(GOSPEL_MATHEW));
        fm.next(state, action);
        assertEquals(0, state.getResource(player, VELLUM, STOREROOM));
        assertEquals(0, state.getResource(player, CANDLE, STOREROOM));
        assertEquals(1, state.getResource(player, PALE_RED_INK, STOREROOM));
        assertEquals(0, state.getResource(player, VIVID_PURPLE_INK, STOREROOM));
        assertEquals(0, state.getResource(player, VIVID_GREEN_INK, STOREROOM));
        assertEquals(GOSPEL_REWARD, state.getVictoryPoints(player));
        assertEquals(1, state.getNumberWritten(GOSPEL_MATHEW));
    }

    @Test
    public void cannotWriteTextIfAllWritten() {
        state.addActionPoints(12);
        WriteText action = new WriteText(GOSPEL_MATHEW);
        int player = state.getCurrentPlayer();
        state.addResource(player, VELLUM, 4);
        state.addResource(player, CANDLE, 4);
        state.addResource(player, PALE_RED_INK, 4);
        state.addResource(player, VIVID_PURPLE_INK, 4);
        state.addResource(player, VIVID_GREEN_INK, 4);
        fm.next(state, action);
        assertEquals(1, state.getNumberWritten(GOSPEL_MATHEW));
        assertTrue(WriteText.meetsRequirements(GOSPEL_MATHEW, state.getStores(player, r -> true)));

        try {
            fm.next(state, action);
            fail("Should throw exception as already written");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }
}
