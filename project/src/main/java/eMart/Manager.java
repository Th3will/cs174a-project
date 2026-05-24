package eMart;

public class Manager {
    private final User user;

    public Manager(User user) {
        this.user = user;
    }

    public String get_eid() {
        return this.user.getId();
    }

    public String get_password() {
        return this.user.getPassword();
    }

    public String get_email() {
        return this.user.getEmail();
    }

    public String get_address() {
        return this.user.getAddress();
    }

    public String get_first_name() {
        return this.user.getFirstName();
    }

    public String get_middle_name() {
        return this.user.getMiddleName();
    }

    public String get_last_name() {
        return this.user.getLastName();
    }
}
