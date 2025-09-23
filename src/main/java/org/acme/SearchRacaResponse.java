package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchRacaResponse {
    public List<Raca> Racas = new ArrayList<>();
    public long TotalRacas;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}