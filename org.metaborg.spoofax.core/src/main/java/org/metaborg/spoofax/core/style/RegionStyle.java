package org.metaborg.spoofax.core.style;

import javax.annotation.Nullable;

import org.metaborg.spoofax.core.messages.ISourceRegion;

public class RegionStyle<T> implements IRegionStyle<T> {
    private final ISourceRegion region;
    private final IStyle style;
    @Nullable private final T fragment;


    public RegionStyle(T fragment, ISourceRegion region, IStyle style) {
        this.fragment = fragment;
        this.region = region;
        this.style = style;
    }



    @Override public ISourceRegion region() {
        return region;
    }

    @Override public IStyle style() {
        return style;
    }

    @Override public @Nullable T fragment() {
        return fragment;
    }


    @Override public String toString() {
        return String.format("RegionStyle [region=%s, style=%s, fragment=%s]", region, style, fragment);
    }
}
