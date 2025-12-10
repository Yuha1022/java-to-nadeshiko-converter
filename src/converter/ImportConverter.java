package converter;

import java.util.ArrayList;
import java.util.List;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;

public class ImportConverter {
    
    // JavaToNadeshikoConverter.javaが参照するItemクラス
    public static class Item {
        public final int line;
        public final String content;
        
        public Item(int line, String content) {
            this.line = line;
            this.content = content;
        }
    }

    /**
     * コンパイルユニット(構文木)全体を走査し、import文を変換する
     * @param cu 構文木
     * @return 変換されたなでしこコードのリスト
     */
    public static List<Item> convert(CompilationUnit cu) {
        List<Item> items = new ArrayList<>();
        
        for (ImportDeclaration importDeclaration : cu.getImports()) {
            // 行番号を取得（行番号がない場合は-1）
            int line = importDeclaration.getBegin().map(p -> p.line).orElse(-1);
            
            // import文をなでしこ形式に変換
            String content = convertImport(importDeclaration); 
            
            // 優先度を50としてリストに追加 (JavaToNadeshikoConverter.javaのロジックに従う)
            items.add(new Item(line, content));
        }
        
        return items;
    }
    
    /**
     * import文をなでしこ形式に変換する
     * @param importDecl import文のAST
     * @return なでしこ形式の文字列
     */
    public static String convertImport(ImportDeclaration importDecl) {
        // import文の完全修飾名を取得
        String importName = importDecl.getNameAsString();
        
        // 通常、なでしこの「取り込む」命令はクラス名またはファイル名のみを使用します。
        // ここでは、完全修飾名からクラス名のみを抽出します。
        String moduleName;
        if (importDecl.isAsterisk()) {
            // import com.example.* の場合、パッケージ名全体をモジュール名とすることが多いため、ここではそのまま使用します
            moduleName = importName;
        } else {
            // import com.example.MyClass の場合、MyClass のみを取得
            moduleName = importName.substring(importName.lastIndexOf('.') + 1);
        }
        
        // ワイルドカードインポート(import xxx.*)の場合
        if (importDecl.isAsterisk()) {
            return "「" + moduleName + ".*」を取り込む。"; // パッケージ全体を取り込むと表現
        }
        
        // 静的インポート(import static xxx)の場合
        if (importDecl.isStatic()) {
            // 静的インポートはなでしこには概念がないため、モジュールとして取り込むのが一般的です
            return "「" + moduleName + "」を取り込む。"; 
        }
        
        // 通常のインポート
        return "「" + moduleName + "」を取り込む。";
    }
}