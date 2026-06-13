package tn.cityvoice.actualiteservice.dto;

import java.util.List;

public class ReactionSummaryDTO {
    private List<ReactionCountDTO> counts;
    private String                 userReaction; // null si pas de réaction
    private int                    total;
    private List<ReactorDTO>       reactors;     // liste des personnes qui ont réagi

    public ReactionSummaryDTO(List<ReactionCountDTO> counts,
                              String userReaction,
                              int total,
                              List<ReactorDTO> reactors) {
        this.counts       = counts;
        this.userReaction = userReaction;
        this.total        = total;
        this.reactors     = reactors;
    }
    public List<ReactionCountDTO> getCounts()      { return counts; }
    public String                 getUserReaction(){ return userReaction; }
    public int                    getTotal()       { return total; }
    public List<ReactorDTO>       getReactors()    { return reactors; }
}
