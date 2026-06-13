package tn.cityvoice.actualiteservice.dto;

public class ReactionCountDTO {
    private String type;
    private int    count;

    public ReactionCountDTO(String type, int count) {
        this.type  = type;
        this.count = count;
    }
    public String getType()  { return type; }
    public int    getCount() { return count; }
}
