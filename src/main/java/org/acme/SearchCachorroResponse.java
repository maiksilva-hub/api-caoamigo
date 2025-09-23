package org.acme;

import java.util.ArrayList;
import java.util.List;

public class SearchCachorroResponse {
    public List<Cachorro> Cachorros = new ArrayList<>();
    public long TotalCachorros;
    public int TotalPages;
    public boolean HasMore;
    public String NextPage;
}