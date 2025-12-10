package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.comments.LineComment;

public class CommentConverter {
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        // 全てのコメントを取得
        List<Comment> comments = cu.getAllContainedComments();
        
        for (Comment comment : comments) {
            int line = comment.getBegin().map(p -> p.line).orElse(-1);
            
            if (comment instanceof LineComment) {
                // 単行コメント（//）の場合
                LineComment lineComment = (LineComment) comment;
                String indent = IndentManager.getIndentForLine(line); // インデントを取得
                String content = lineComment.getContent().trim();
                // //の後の内容をそのまま使用
                items.add(new Item(line, indent + "//" + content, 5));
            } else if (comment instanceof BlockComment) {
                // ブロックコメント（/* */）の場合
                BlockComment blockComment = (BlockComment) comment;
                String content = blockComment.getContent();
                
                // 複数行のブロックコメントの場合
                if (content.contains("\n")) {
                    // 各行を分割して処理
                    String indent = IndentManager.getIndentForLine(line); // 最初の行のインデントを取得
                    String[] lines = content.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        String lineContent = lines[i];
                        // Javadoc形式の行頭の * を削除。
                        if (lineContent.trim().startsWith("*")) {
                            lineContent = lineContent.replaceFirst("\\*", " "); // アスタリスクをスペースに置き換えてインデントを維持
                        }
                        // 最後の行が空で、コメントの終了を示すだけの場合はスキップ
                        if (i == lines.length - 1 && lineContent.trim().isEmpty()) continue;

                        if (i == 0) {
                            items.add(new Item(line, indent + "/*" + lineContent, 5));
                        } else {
                            items.add(new Item(line + i, indent + lineContent, 5));
                        }
                    }
                    // 最後の行の次の行に */ を追加
                    items.add(new Item(line + lines.length, indent + "*/", 5));
                } else {
                    // 単行のブロックコメント
                    String indent = IndentManager.getIndentForLine(line); // インデントを取得
                    items.add(new Item(line, indent + "/*" + content + "*/", 5));
                }
            }
        }
        
        return items;
    }
    
    public static class Item {
        public final int line;
        public final String content;
        public final int priority;
        
        public Item(int line, String content, int priority) {
            this.line = line;
            this.content = content;
            this.priority = priority;
        }
    }
}