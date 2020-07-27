package edu.project.secret_messenger.object;

/**
 * TODO 로그인 유무 체크
 */

public class User {
    private String id;
    private String pw;
    private String name;
    private boolean is_Login;

    public User(){}

    public User(String id, String pw, String name){
        this.id = id; this.pw = pw; this.name = name;
    }

    public void setId(String id){ this.id = id;}
    public void setPw(String pw){ this.pw = pw;}
    public void setName(String name){ this.name = name;}
    public String getId() { return id;}
    public String getName() { return name;}
    public String getPw() { return pw;}
    public boolean isIs_Login() { return is_Login; }
    public void setIs_Login(boolean is_Login) { this.is_Login = is_Login; }
}
