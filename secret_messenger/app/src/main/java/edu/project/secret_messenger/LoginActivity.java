package edu.project.secret_messenger;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.security.InvalidKeyException;
import java.util.regex.Pattern;

import edu.project.secret_messenger.util.SaveSharedPreference;

import static edu.project.secret_messenger.util.SaveSharedPreference.*;
import static edu.project.secret_messenger.util.util.*;

public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private EditText idEdit;
    private EditText pwEdit;
    private Button loginBtn;
    private Button signupBtn;
    private CheckBox autoLoginCheck;
    private String id;
    private String pw;
    private String loginID;
    private String myName;
    private boolean is_auto;
    private boolean is_login;
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference myRef = database.getReference("user/");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);


        idEdit = (EditText)findViewById(R.id.login_id);
        idEdit.setFilters(new InputFilter[]{filterAlphaNum});
        pwEdit = (EditText)findViewById(R.id.login_password);
        pwEdit.setFilters(new InputFilter[]{filterAlphaNum});
        loginBtn = (Button)findViewById(R.id.login_button);
        signupBtn = (Button)findViewById(R.id.signup_button);
        autoLoginCheck = (CheckBox)findViewById(R.id.autoLoginCheck);



        final SharedPreferences auto = getSharedPreferences("auto", Activity.MODE_PRIVATE);

        loginID = SaveSharedPreference.getId(this.getApplicationContext());
        pw = SaveSharedPreference.getPw(this.getApplicationContext());

        if(loginID !=null && pw != null){
            Intent intent = new Intent(LoginActivity.this,LobbyActivity.class);
            myRef = database.getReference("user").child(loginID);
            myRef.child("isLogin").setValue(true);
            myRef.addListenerForSingleValueEvent(new ValueEventListener() { // 사용자 ID로부터 이름을 받아옴
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    myName = snapshot.child("name").getValue(String.class);
                    Toast.makeText(getApplicationContext(),myName+"님 자동로그인 입니다.", Toast.LENGTH_SHORT).show();
                }
                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                }
            });

            intent.putExtra("myID",loginID);
            startActivity(intent);
            LoginActivity.this.finish();
        }
        loginBtn.setOnClickListener(new Button.OnClickListener(){ // 로그인 버튼 클릭리스너
            @Override
            public void onClick(View view){
                Log.i(TAG,"로그인 버튼 클릭");
                try {
                    id = idEdit.getText().toString();
                    loginID = hashStr(id); // 입력받은 ID 해시로 저장
                    pw = hashStr(pwEdit.getText().toString()); // 입력받은 패스워드 해시로 저장
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }

                Query query = myRef.orderByChild("id").equalTo(loginID);

                query.addListenerForSingleValueEvent(new ValueEventListener() { // user 내 로그인한 ID 찾기
                    @Override
                    public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                        if(dataSnapshot.getValue()==null) {
                            Toast.makeText(LoginActivity.this.getApplicationContext(), "등록된 ID가 없습니다.\n입력 ID : " + loginID, Toast.LENGTH_SHORT).show();
                            idEdit.setText(null);
                            pwEdit.setText(null);
                        }
                        else {
                            for (DataSnapshot datas : dataSnapshot.getChildren()) { // ID가 존재할 시 그 ID를 키 값으로 설정
                                loginID = datas.getKey();
                            }

                            myRef.child(loginID).child("pw").addValueEventListener(new ValueEventListener() { // 입력받은 pw가 id에 있는 pw와 일치한지 확인.
                                @Override
                                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                    if(isPwEqual(dataSnapshot,pw)){
                                        if(autoLoginCheck.isChecked())
                                            is_auto = true;
                                        else
                                            is_auto = false;
                                        Toast.makeText(LoginActivity.this.getApplicationContext(), "로그인 성공.\n ID : " + id, Toast.LENGTH_SHORT).show();
                                        Intent intent = new Intent(LoginActivity.this,LobbyActivity.class);
                                        intent.putExtra("myID",loginID);
                                        DatabaseReference ref = database.getReference("user").child(loginID).child("isLogin");
                                        ref.setValue(true);
                                        if(is_auto) {
                                            setAuto(getApplicationContext(),loginID,pw);
                                            Log.e(TAG, "자동로그인 활성화"+loginID+" "+pw);
                                        }
                                        startActivity(intent);
                                        LoginActivity.this.finish();
                                    }
                                    else{
                                        Toast.makeText(LoginActivity.this.getApplicationContext(), "비밀번호가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
                                        pwEdit.setText(null);
                                    }
                                }
                                @Override
                                public void onCancelled(@NonNull DatabaseError databaseError) {

                                }
                            });
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError databaseError) {

                    }
                });
            }
        });

        signupBtn.setOnClickListener(new Button.OnClickListener(){ //회원가입 버튼 클릭리스너
            @Override
            public void onClick(View view){
                Intent intent = new Intent(LoginActivity.this, signupActivity.class);
                startActivity(intent);
            }
        });

    }

    private boolean isPwEqual(DataSnapshot snap, String pw){ // 입력받은 패스워드와 DB에 있는 패스워드를 비교
        boolean result;
        if(snap.getValue().equals(pw)){
            result = true;
        } else {
            result = false;
        }
        return result;
    }

    @Override
    public void onStart(){
        super.onStart();
    }
    @Override
    protected void onResume() {
        super.onResume();
    }


    @Override
    protected void onPause() {
        super.onPause();
    }
    protected InputFilter filterAlphaNum = new InputFilter() {
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            Pattern ps = Pattern.compile("^[a-zA-Z0-9]+$");

            if (!ps.matcher(source).matches()) {
                return "";
            }
            return null;
        }
    };

}
