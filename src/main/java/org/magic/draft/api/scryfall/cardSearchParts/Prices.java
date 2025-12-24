package org.magic.draft.api.scryfall.cardSearchParts;

public class Prices {
    private String usd;
    private String usdFoil;
    private Object usdEtched;
    private String eur;
    private String eurFoil;
    private String tix;

    public String getUsd() { return usd; }
    public void setUsd(String value) { this.usd = value; }

    public String getUsdFoil() { return usdFoil; }
    public void setUsdFoil(String value) { this.usdFoil = value; }

    public Object getUsdEtched() { return usdEtched; }
    public void setUsdEtched(Object value) { this.usdEtched = value; }

    public String getEur() { return eur; }
    public void setEur(String value) { this.eur = value; }

    public String getEurFoil() { return eurFoil; }
    public void setEurFoil(String value) { this.eurFoil = value; }

    public String getTix() { return tix; }
    public void setTix(String value) { this.tix = value; }
}
