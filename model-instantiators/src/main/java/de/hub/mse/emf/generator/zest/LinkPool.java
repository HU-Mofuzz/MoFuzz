package de.hub.mse.emf.generator.zest;

import com.pholser.junit.quickcheck.random.SourceOfRandomness;

import java.util.ArrayList;
import java.util.List;

public class LinkPool<L> {

    private List<L> pool = new ArrayList<>();

    public void add(L link) {
        pool.add(link);
    }

    public int getSize() {
        return pool.size();
    }

    public boolean isEmpty() {
        return pool.isEmpty();
    }

    public L getRandomLink(SourceOfRandomness source) {
        int index = source.nextInt(pool.size());
        return pool.get(index);
    }
}
