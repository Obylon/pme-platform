package com.pme.community.domain;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ESSAI E4 (Phase 2.2) - code métier volontairement non testé.
 * Cette classe ne doit pas être fusionnée : elle sert à vérifier que le quality gate
 * SonarQube bloque une baisse de couverture sur le nouveau code.
 */
public final class ListingStatistics {

    private ListingStatistics() {
    }

    public static Map<Category, Integer> countByCategory(List<Listing> listings) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        for (Listing listing : listings) {
            counts.merge(listing.getCategory(), 1, Integer::sum);
        }
        return counts;
    }

    public static Category mostPopular(List<Listing> listings) {
        Category best = null;
        int bestCount = -1;
        for (Map.Entry<Category, Integer> entry : countByCategory(listings).entrySet()) {
            if (entry.getValue() > bestCount) {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best;
    }

    public static double givenAwayRatio(List<Listing> listings) {
        if (listings.isEmpty()) {
            return 0.0;
        }
        long given = listings.stream().filter(l -> l.getType() == ListingType.GIVEAWAY).count();
        return (double) given / listings.size();
    }
}
