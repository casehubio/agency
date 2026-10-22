package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = AggregationSpec.PassThrough.class, name = "pass-through"),
        @Type(value = AggregationSpec.CollectAll.class, name = "collect-all"),
        @Type(value = AggregationSpec.MajorityVote.class, name = "majority-vote"),
        @Type(value = AggregationSpec.Auction.class, name = "auction")
})
public sealed interface AggregationSpec {

    record PassThrough() implements AggregationSpec {}

    record CollectAll() implements AggregationSpec {}

    record MajorityVote() implements AggregationSpec {}

    record Auction(@Nullable String auctionType) implements AggregationSpec {}
}
