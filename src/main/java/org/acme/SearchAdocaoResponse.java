package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchAdocaoResponse {
    public List<Adocao> Adocoes = new ArrayList<>();
    public long TotalAdocoes;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}