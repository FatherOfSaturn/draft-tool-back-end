package org.magic.draft.api.scryfall.cardSearchParts;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class ScryfallCard {
    private String object;
    private UUID id;
    private UUID oracleId;
    private List<Long> multiverseIds;
    private Long mtgoId;
    private Long tcgplayerId;
    private Long cardmarketId;
    private String name;
    private String lang;
    private LocalDate releasedAt;
    private String uri;
    private String scryfallUri;
    private String layout;
    private Boolean highresImage;
    private String imageStatus;
    private ImageUris imageUris;
    private String manaCost;
    private Long cmc;
    private String typeLine;
    private String oracleText;
    private String power;
    private String toughness;
    private List<String> colors;
    private List<String> colorIndicator;
    private List<String> colorIdentity;
    private List<Object> keywords;
    private List<AllPart> allParts;
    private Legalities legalities;
    private List<String> games;
    private Boolean reserved;
    private Boolean gameChanger;
    private Boolean foil;
    private Boolean nonfoil;
    private List<String> finishes;
    private Boolean oversized;
    private Boolean promo;
    private Boolean reprint;
    private Boolean variation;
    private UUID setId;
    private String set;
    private String setName;
    private String setType;
    private String setUri;
    private String setSearchUri;
    private String scryfallSetUri;
    private String rulingsUri;
    private String printsSearchUri;
    private String collectorNumber;
    private Boolean digital;
    private String rarity;
    private UUID cardBackId;
    private String artist;
    private List<UUID> artistIds;
    private UUID illustrationId;
    private String borderColor;
    private String frame;
    private List<String> frameEffects;
    private String securityStamp;
    private Boolean fullArt;
    private Boolean textless;
    private Boolean booster;
    private Boolean storySpotlight;
    private Long edhrecRank;
    private Long pennyRank;
    private Preview preview;
    private Prices prices;
    private RelatedUris relatedUris;
    private PurchaseUris purchaseUris;

    public String getObject() { return object; }
    public void setObject(String value) { this.object = value; }

    public UUID getId() { return id; }
    public void purpleSetId(UUID value) { this.id = value; }

    public UUID getOracleId() { return oracleId; }
    public void setOracleId(UUID value) { this.oracleId = value; }

    public List<Long> getMultiverseIds() { return multiverseIds; }
    public void setMultiverseIds(List<Long> value) { this.multiverseIds = value; }

    public Long getMtgoId() { return mtgoId; }
    public void setMtgoId(Long value) { this.mtgoId = value; }

    public Long getTcgplayerId() { return tcgplayerId; }
    public void setTcgplayerId(Long value) { this.tcgplayerId = value; }

    public Long getCardmarketId() { return cardmarketId; }
    public void setCardmarketId(Long value) { this.cardmarketId = value; }

    public String getName() { return name; }
    public void purpleSetName(String value) { this.name = value; }

    public String getLang() { return lang; }
    public void setLang(String value) { this.lang = value; }

    public LocalDate getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDate value) { this.releasedAt = value; }

    public String getUri() { return uri; }
    public void purpleSetUri(String value) { this.uri = value; }

    public String getScryfallUri() { return scryfallUri; }
    public void setScryfallUri(String value) { this.scryfallUri = value; }

    public String getLayout() { return layout; }
    public void setLayout(String value) { this.layout = value; }

    public Boolean getHighresImage() { return highresImage; }
    public void setHighresImage(Boolean value) { this.highresImage = value; }

    public String getImageStatus() { return imageStatus; }
    public void setImageStatus(String value) { this.imageStatus = value; }

    public ImageUris getImageUris() { return imageUris; }
    public void setImageUris(ImageUris value) { this.imageUris = value; }

    public String getManaCost() { return manaCost; }
    public void setManaCost(String value) { this.manaCost = value; }

    public Long getCmc() { return cmc; }
    public void setCmc(Long value) { this.cmc = value; }

    public String getTypeLine() { return typeLine; }
    public void setTypeLine(String value) { this.typeLine = value; }

    public String getOracleText() { return oracleText; }
    public void setOracleText(String value) { this.oracleText = value; }

    public String getPower() { return power; }
    public void setPower(String value) { this.power = value; }

    public String getToughness() { return toughness; }
    public void setToughness(String value) { this.toughness = value; }

    public List<String> getColors() { return colors; }
    public void setColors(List<String> value) { this.colors = value; }

    public List<String> getColorIndicator() { return colorIndicator; }
    public void setColorIndicator(List<String> value) { this.colorIndicator = value; }

    public List<String> getColorIdentity() { return colorIdentity; }
    public void setColorIdentity(List<String> value) { this.colorIdentity = value; }

    public List<Object> getKeywords() { return keywords; }
    public void setKeywords(List<Object> value) { this.keywords = value; }

    public List<AllPart> getAllParts() { return allParts; }
    public void setAllParts(List<AllPart> value) { this.allParts = value; }

    public Legalities getLegalities() { return legalities; }
    public void setLegalities(Legalities value) { this.legalities = value; }

    public List<String> getGames() { return games; }
    public void setGames(List<String> value) { this.games = value; }

    public Boolean getReserved() { return reserved; }
    public void setReserved(Boolean value) { this.reserved = value; }

    public Boolean getGameChanger() { return gameChanger; }
    public void setGameChanger(Boolean value) { this.gameChanger = value; }

    public Boolean getFoil() { return foil; }
    public void setFoil(Boolean value) { this.foil = value; }

    public Boolean getNonfoil() { return nonfoil; }
    public void setNonfoil(Boolean value) { this.nonfoil = value; }

    public List<String> getFinishes() { return finishes; }
    public void setFinishes(List<String> value) { this.finishes = value; }

    public Boolean getOversized() { return oversized; }
    public void setOversized(Boolean value) { this.oversized = value; }

    public Boolean getPromo() { return promo; }
    public void setPromo(Boolean value) { this.promo = value; }

    public Boolean getReprint() { return reprint; }
    public void setReprint(Boolean value) { this.reprint = value; }

    public Boolean getVariation() { return variation; }
    public void setVariation(Boolean value) { this.variation = value; }

    public UUID getSetId() { return setId; }
    public void setSetId(UUID value) { this.setId = value; }

    public String getSet() { return set; }
    public void setSet(String value) { this.set = value; }

    public String getSetName() { return setName; }
    public void setSetName(String value) { this.setName = value; }

    public String getSetType() { return setType; }
    public void setSetType(String value) { this.setType = value; }

    public String getSetUri() { return setUri; }
    public void setSetUri(String value) { this.setUri = value; }

    public String getSetSearchUri() { return setSearchUri; }
    public void setSetSearchUri(String value) { this.setSearchUri = value; }

    public String getScryfallSetUri() { return scryfallSetUri; }
    public void setScryfallSetUri(String value) { this.scryfallSetUri = value; }

    public String getRulingsUri() { return rulingsUri; }
    public void setRulingsUri(String value) { this.rulingsUri = value; }

    public String getPrintsSearchUri() { return printsSearchUri; }
    public void setPrintsSearchUri(String value) { this.printsSearchUri = value; }

    public String getCollectorNumber() { return collectorNumber; }
    public void setCollectorNumber(String value) { this.collectorNumber = value; }

    public Boolean getDigital() { return digital; }
    public void setDigital(Boolean value) { this.digital = value; }

    public String getRarity() { return rarity; }
    public void setRarity(String value) { this.rarity = value; }

    public UUID getCardBackId() { return cardBackId; }
    public void setCardBackId(UUID value) { this.cardBackId = value; }

    public String getArtist() { return artist; }
    public void setArtist(String value) { this.artist = value; }

    public List<UUID> getArtistIds() { return artistIds; }
    public void setArtistIds(List<UUID> value) { this.artistIds = value; }

    public UUID getIllustrationId() { return illustrationId; }
    public void setIllustrationId(UUID value) { this.illustrationId = value; }

    public String getBorderColor() { return borderColor; }
    public void setBorderColor(String value) { this.borderColor = value; }

    public String getFrame() { return frame; }
    public void setFrame(String value) { this.frame = value; }

    public List<String> getFrameEffects() { return frameEffects; }
    public void setFrameEffects(List<String> value) { this.frameEffects = value; }

    public String getSecurityStamp() { return securityStamp; }
    public void setSecurityStamp(String value) { this.securityStamp = value; }

    public Boolean getFullArt() { return fullArt; }
    public void setFullArt(Boolean value) { this.fullArt = value; }

    public Boolean getTextless() { return textless; }
    public void setTextless(Boolean value) { this.textless = value; }

    public Boolean getBooster() { return booster; }
    public void setBooster(Boolean value) { this.booster = value; }

    public Boolean getStorySpotlight() { return storySpotlight; }
    public void setStorySpotlight(Boolean value) { this.storySpotlight = value; }

    public Long getEdhrecRank() { return edhrecRank; }
    public void setEdhrecRank(Long value) { this.edhrecRank = value; }

    public Long getPennyRank() { return pennyRank; }
    public void setPennyRank(Long value) { this.pennyRank = value; }

    public Preview getPreview() { return preview; }
    public void setPreview(Preview value) { this.preview = value; }

    public Prices getPrices() { return prices; }
    public void setPrices(Prices value) { this.prices = value; }

    public RelatedUris getRelatedUris() { return relatedUris; }
    public void setRelatedUris(RelatedUris value) { this.relatedUris = value; }

    public PurchaseUris getPurchaseUris() { return purchaseUris; }
    public void setPurchaseUris(PurchaseUris value) { this.purchaseUris = value; }
}
