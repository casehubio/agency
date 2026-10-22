package io.casehub.agency.orchestration.aggregation;

import org.jspecify.annotations.Nullable;

import java.util.List;

public record AuctionOutcome(
        @Nullable Bid winningBid,
        List<Bid> allBids,
        boolean sold
) {
    public AuctionOutcome {
        allBids = List.copyOf(allBids);
    }
}
