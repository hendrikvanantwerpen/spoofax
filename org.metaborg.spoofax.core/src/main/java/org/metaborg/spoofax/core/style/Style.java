package org.metaborg.spoofax.core.style;

import java.awt.Color;

import javax.annotation.Nullable;

public class Style implements IStyle {
    private static final long serialVersionUID = -8435127070824031921L;
    
	private final @Nullable Color color;
    private final @Nullable Color backgroundColor;
    private final boolean bold;
    private final boolean italic;
    private final boolean underscore;


    public Style(@Nullable Color color, @Nullable Color backgroundColor, boolean bold, boolean italic,
        boolean underscore) {
        this.color = color;
        this.backgroundColor = backgroundColor;
        this.bold = bold;
        this.italic = italic;
        this.underscore = underscore;
    }


    @Override public @Nullable Color color() {
        return color;
    }

    @Override public @Nullable Color backgroundColor() {
        return backgroundColor;
    }

    @Override public boolean bold() {
        return bold;
    }

    @Override public boolean italic() {
        return italic;
    }

    @Override public boolean underscore() {
        return underscore;
    }


    @Override public String toString() {
        return String.format("Style [color=%s, backgroundColor=%s, bold=%s, italic=%s, underscore=%s]", color,
            backgroundColor, bold, italic, underscore);
    }
}
