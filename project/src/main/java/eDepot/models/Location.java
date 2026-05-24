package eDepot.models;

public class Location {
    private String letter;
    private int number;

    public Location() {
    }

    public Location(String letter, int number) {
        this.letter = letter;
        this.number = number;
    }

    public String getLetter() {
        return letter;
    }

    public void setLetter(String letter) {
        if (letter != null) {
            this.letter = letter.toUpperCase();
        } 
        else {
            this.letter = null;
        }
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    // Instead of printing memory addresses, System.out.println(myLocation) will print actual data.
    @Override
    public String toString() {
        return "Location{" +
                "letter='" + letter + '\'' +
                ", number=" + number +
                '}';
    }
}