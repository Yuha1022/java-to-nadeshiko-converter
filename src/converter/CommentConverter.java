package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.Comment;

public class CommentConverter {
    public static List<Item> convert(CompilationUnit cu) { 
        List<Item> items = new ArrayList<>();
        for (Comment comment : cu.getAllContainedComments()) { //全てのコメントに対して
            int line = comment.getBegin().map(p -> p.line).orElse(-1); //行番号を取得
            String text = "//" + comment.getContent().trim(); //コメントを変換
            items.add(new Item(line, text)); //リストに追加
        }
        return items;
    }
    public static class Item { //行番号と内容を保持するクラス
        public final int line;
        public final String content;
        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }
}