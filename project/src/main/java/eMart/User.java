package eMart;

public class User {
    private String id;
    private String password;
    private String email;
    private String address;
    private String firstName;
    private String middleName;
    private String lastName;

    public User(String id, String password, String email, String address, String firstName, String middleName, String lastName) {
        this.id = id;
        this.password = password;
        this.email = email;
        this.address = address;
        this.firstName = firstName;
        this.middleName = middleName;
        this.lastName = lastName;
    }

    public String getId() {
        return id;
    }

    public String getPassword() {
        return password;
    }

    public String getEmail() {
        return email;
    }

    public String getAddress() {
        return address;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public String getLastName() {
        return lastName;
    }
}
