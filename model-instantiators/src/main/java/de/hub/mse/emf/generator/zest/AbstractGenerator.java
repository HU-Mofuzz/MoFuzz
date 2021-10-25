package de.hub.mse.emf.generator.zest;

import com.pholser.junit.quickcheck.generator.GenerationStatus;
import com.pholser.junit.quickcheck.generator.Generator;
import com.pholser.junit.quickcheck.random.SourceOfRandomness;

/**
 * Abstract Genertor for multi document generation
 * @param <D> The target document type, e.g. {@link org.eclipse.emf.ecore.resource.Resource} or {@link String} for a file path
 * @param <L> The Link type between multiple documents, e.g. {@link String} in SVG/XML xlink hrefs
 * @param <C> The configuration type for the generation and preparation
 */
public abstract class AbstractGenerator<D, L, C> extends Generator<D> {

    protected final C config;
    private boolean prepared = false;

    private LinkPool<L> linkPool;

    protected AbstractGenerator(Class<D> type, C config) {
        super(type);
        this.config = config;
    }

    abstract LinkPool<L> prepare();
    abstract D internalExecute(LinkPool<L> linkPool);

    @Override
    public D generate(SourceOfRandomness sourceOfRandomness, GenerationStatus generationStatus) {
        if(!prepared) {
            linkPool = prepare();
            if(linkPool.isEmpty()) {
                throw new IllegalStateException("No links to reference!");
            }
            prepared = true;
        }
        return internalExecute(linkPool);
    }
}
