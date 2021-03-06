package org.metaborg.spoofax.core.syntax;

import javax.annotation.Nullable;

import org.apache.commons.vfs2.FileObject;
import org.metaborg.spoofax.core.language.ILanguage;
import org.metaborg.spoofax.core.messages.ISourceRegion;

/**
 * Interface for parsing, unparsing, and retrieving origin information.
 *
 * @param <T>
 *            Type of the parse result.
 */
public interface ISyntaxService<T> {
    /**
     * Parses a resource, using parsing rules from given language.
     * 
     * @param text
     *            Text to parse.
     * @param resource
     *            Resource associated with the {@code text} to parse.
     * @param language
     *            Language to parse with.
     * @return Result of parsing.
     * @throws ParseException
     *             when parsing fails unexpectedly.
     */
    public abstract ParseResult<T> parse(String text, FileObject resource, ILanguage language) throws ParseException;

    /**
     * Unparses a parsed fragment back into a string, using unparsing rules from given language.
     * 
     * @param parsed
     *            Parsed fragment.
     * @param language
     *            Language to unparse with.
     * @return Unparsed string.
     */
    public abstract String unparse(T parsed, ILanguage language);

    /**
     * Attempts to retrieve the source region for given parsed fragment.
     * 
     * @param parsed
     *            Parsed fragment.
     * 
     * @return Source region for parsed fragment, or null if no source region can be retrieved.
     */
    public abstract @Nullable ISourceRegion region(T parsed);

    /**
     * @return Single line comment prefix characters for given language.
     */
    public abstract Iterable<String> singleLineCommentPrefixes(ILanguage language);

    /**
     * @return Multi line comment prefix and postfix characters for given language.
     */
    public abstract Iterable<MultiLineCommentCharacters> multiLineCommentCharacters(ILanguage language);

    /**
     * @return Fence (brackets, parentheses, etc.) open and close characters for given language.
     */
    public abstract Iterable<FenceCharacters> fenceCharacters(ILanguage language);
}
