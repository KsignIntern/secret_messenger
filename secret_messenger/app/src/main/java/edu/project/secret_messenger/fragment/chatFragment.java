package edu.project.secret_messenger.fragment;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import edu.project.secret_messenger.ARIA_CBC.Aria_CBC;
import edu.project.secret_messenger.R;
import edu.project.secret_messenger.object.ChatDTO;

import static edu.project.secret_messenger.util.*;

public class chatFragment extends Fragment {
    private static final String TAG = "chatFragment";
    private FirebaseDatabase database = FirebaseDatabase.getInstance();
    private DatabaseReference ref;

    private EditText chatEdit;
    private EditText encKeyEdit;
    private CheckBox enc_CheckBox;
    private Button sendButton;
    private ListView listView;

    private ChatDTO chatDTO;
    private ArrayList<ChatDTO> chatDTOs = new ArrayList<ChatDTO>();
    private ChatListAdapter chatListAdapter;
    private String myID;
    private String mUserName;
    private String message;
    private Date msg_Time;
    private String encKey;
    private boolean is_Enc;
    private Aria_CBC aria;
    private byte[] primeVec = hexStringToByteArray("0f 1e 2d 3c 4b 5a 69 78 87 96 a5 b4 c3 d2 e1 f0".replaceAll(" ", ""));;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final FrameLayout layout = (FrameLayout) inflater.inflate(R.layout.fragment_chatlist, container, false);
        listView = (ListView)layout.findViewById(R.id.chat_listView);
        chatListAdapter = new ChatListAdapter(getContext(),R.layout.chat_list_row,chatDTOs);
        listView.setAdapter(chatListAdapter);
        enc_CheckBox = (CheckBox)layout.findViewById(R.id.enc_check);
        encKeyEdit = (EditText)layout.findViewById(R.id.encKey);
        chatEdit = (EditText)layout.findViewById(R.id.chat_edit);
        sendButton = (Button)layout.findViewById(R.id.sendButton);

        myID = getArguments().getString("myID");
        getUserName(myID);
        Log.e(TAG,"내 이름은 "+mUserName);

        ref = database.getReference("user").child(myID);
        ref.addListenerForSingleValueEvent(new ValueEventListener() { // 사용자 ID로부터 이름을 받아옴
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mUserName = snapshot.child("name").getValue(String.class);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });


        ref = database.getReference("messages");
        ref.addListenerForSingleValueEvent(new ValueEventListener() { // 처음 채팅목록 받아오기
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if(chatListAdapter!=null)
                    chatListAdapter.clear();
                for(DataSnapshot datas: snapshot.getChildren()){
                    chatListAdapter.add(datas.getValue(ChatDTO.class));
                }
                chatListAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        super.onChanged();
                        listView.setSelection(chatListAdapter.getCount()-1);
                    }
                });
                listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        ref.addChildEventListener(new ChildEventListener() { // 채팅이 추가/삭제 될 때 마다 리스트어댑터에 추가/삭제
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                chatListAdapter.add(snapshot.getValue(ChatDTO.class));
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                chatListAdapter.remove(snapshot.getValue(ChatDTO.class));
            }

            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {

            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });

        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() { // 채팅 길게 클릭했을 때 클릭 리스너
            @Override
            public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id) {
                Log.e(TAG,"listview LongClick");

                final int pos = position;
                final CharSequence[] items = {"삭제","비밀메시지 보기"};
                AlertDialog.Builder dialog = new AlertDialog.Builder(view.getContext(),android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
                dialog.setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        switch(i){
                            case 0:
                                if(chatDTOs.get(pos).getUserID().equals(myID)){ // 선택한 채팅이 사용자가 작성한 것인지 확인 후 삭제.
                                    String msgUid =chatDTOs.get(pos).getMsgUID();
                                    chatDTOs.remove(pos);
                                    chatListAdapter.notifyDataSetChanged();

                                    ref = database.getReference("messages").child(msgUid);
                                    ref.removeValue();

                                    listView.clearChoices();
                                }
                                else
                                    Toast.makeText(getContext(), "이 채팅의 사용자가 아니여서 삭제 불가", Toast.LENGTH_SHORT).show();
                                break;
                            case 1:
                                if(chatDTOs.get(pos).getIs_Enc()==true){ // 비밀메시지인지 확인 후 복호화
                                    final EditText decKeytext = new EditText(getView().getContext());
                                    AlertDialog.Builder decKeyDialog = new AlertDialog.Builder(getView().getContext(),android.R.style.Theme_DeviceDefault_Light_Dialog_Alert);
                                    decKeyDialog.setTitle("비밀번호");
                                    decKeyDialog.setView(decKeytext);
                                    decKeyDialog.setPositiveButton("입력",
                                            new DialogInterface.OnClickListener() {
                                                @Override
                                                public void onClick(DialogInterface dialogInterface, int i) {
                                                    encKey = decKeytext.getText().toString();
                                                    aria = new Aria_CBC(encKey);
                                                    String cipherMsg = chatDTOs.get(pos).getMessage();
                                                    String plain=aria.Decrypt(cipherMsg);
                                                    Log.e(TAG,"복호화한 문장: "+plain);
                                                }
                                            });
                                    decKeyDialog.show();
                                }
                                else
                                    Toast.makeText(getContext(), "비밀메시지가 아닙니다", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
                dialog.create().show();

                return true;
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() { // 전송 버튼 클릭 리스너
            @Override
            public void onClick(View view) {
                if(enc_CheckBox.isChecked())
                    is_Enc = true;
                else
                    is_Enc = false;

                ref = database.getReference("messages");
                String msgUid = ref.push().getKey();
                message = chatEdit.getText().toString();
                msg_Time = new Date();
                if(is_Enc==true){ // 비밀메시지 체크가 되어있으면 암호화하여 DB에 저장.
                    encKey = encKeyEdit.getText().toString();
                    aria = new Aria_CBC(encKey);
                    String cipher = aria.Encrypt(message);
                    chatDTO = new ChatDTO(msgUid,myID,cipher,mUserName,msg_Time,is_Enc);
                }
                else
                    chatDTO = new ChatDTO(msgUid,myID,message,mUserName,msg_Time,is_Enc);

                Map<String,Object> chatValues = chatDTO.toMap();
                Map<String,Object> childUpdates = new HashMap<>();
                childUpdates.put(msgUid,chatValues);
                ref.updateChildren(childUpdates);

                chatEdit.setText("");
                encKeyEdit.setText("");
                enc_CheckBox.setChecked(false);
            }
        });

        return layout;
    }

    private class ChatListAdapter extends ArrayAdapter<ChatDTO> {
        private ArrayList<ChatDTO> items;
        private ChatDTO chatDTO;

        public ChatListAdapter(Context context, int textViewResourceId, ArrayList<ChatDTO> items) {
            super(context, textViewResourceId, items);
            this.items = items;
        }
        @Override
        public View getView(int position, View convertView, final ViewGroup parent) {
            View view = convertView;
            Log.e(TAG,"getView");
            if (view == null) {
                LayoutInflater inflater = (LayoutInflater)parent.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                view = inflater.inflate(R.layout.chat_list_row,null);
            }
            chatDTO = items.get(position);
            if (chatDTO != null) {
                TextView userName = (TextView) view.findViewById(R.id.chat_userName);
                TextView chatMsg = (TextView) view.findViewById(R.id.chat_message);
                TextView chatTime = (TextView) view.findViewById(R.id.chat_time);

                if(chatMsg != null){
                    if(chatDTO.getIs_Enc()==false){
                        chatMsg.setText(chatDTO.getMessage());
                        view.setBackgroundResource(R.drawable.white_edittext);
                    }
                    else {
                        chatMsg.setText("비밀메시지입니다.");
                        view.setBackgroundResource(R.drawable.red_edittext);
                    }
                }
                if(userName != null){
                    userName.setText(chatDTO.getUserName());
                }
                if(chatTime!=null){
                    SimpleDateFormat date = new SimpleDateFormat("yyyy/MM/dd");
                    SimpleDateFormat time = new SimpleDateFormat("hh:mm:ss a");
                    String this_Time = date.format(chatDTO.getMsg_Time())+" " +time.format(chatDTO.getMsg_Time());
                    chatTime.setText(this_Time);
                }
            }

            return view;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
    }

    public void getUserName(String id){
        ref = database.getReference("user").child(id);
        ref.addListenerForSingleValueEvent(new ValueEventListener() { // 사용자 ID로부터 이름을 받아옴
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                mUserName = snapshot.child("name").getValue(String.class);
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
            }
        });
    }

}