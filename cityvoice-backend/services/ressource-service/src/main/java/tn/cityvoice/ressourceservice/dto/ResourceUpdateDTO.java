package tn.cityvoice.ressourceservice.dto;

import org.springframework.web.multipart.MultipartFile;

public class ResourceUpdateDTO {
    private String nom;
    private String type;
    private String etat;
    private double valeur;
    private int dureeVieEstimee;
    private String dateAchat;
    private MultipartFile file;

    // Getters
    public String getNom() { return nom; }
    public String getType() { return type; }
    public String getEtat() { return etat; }
    public double getValeur() { return valeur; }
    public int getDureeVieEstimee() { return dureeVieEstimee; }
    public String getDateAchat() { return dateAchat; }
    public MultipartFile getFile() { return file; }

    // Setters
    public void setNom(String nom) { this.nom = nom; }
    public void setType(String type) { this.type = type; }
    public void setEtat(String etat) { this.etat = etat; }
    public void setValeur(double valeur) { this.valeur = valeur; }
    public void setDureeVieEstimee(int dureeVieEstimee) { this.dureeVieEstimee = dureeVieEstimee; }
    public void setDateAchat(String dateAchat) { this.dateAchat = dateAchat; }
    public void setFile(MultipartFile file) { this.file = file; }
}