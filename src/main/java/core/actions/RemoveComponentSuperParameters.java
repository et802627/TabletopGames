package core.actions;

import core.AbstractGameState;
import core.components.Component;
import core.components.Deck;

import java.util.Objects;

public class RemoveComponentSuperParameters {

    private int deckFrom;
    private int deckTo;
    private int fromIndex;

    public RemoveComponentSuperParameters() {}

    public RemoveComponentSuperParameters(int deckFrom, int deckTo, int fromIndex) {
        this.deckFrom = deckFrom;
        this.deckTo = deckTo;
        this.fromIndex = fromIndex;
    }

    public int getDeckFrom() {
        return deckFrom;
    }

    public int getDeckTo() {
        return deckTo;
    }

    public int getFromIndex() {
        return fromIndex;
    }
}
