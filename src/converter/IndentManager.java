package converter;

import java.util.HashMap;
import java.util.Map;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.stmt.Statement;

/**
 * 全てのインデント情報を一元管理するクラス
 */
public class IndentManager {

    private static Map<Integer, String> indentMap = new HashMap<>();

    /**
     * 変換処理の開始時にインデント情報をクリアする
     */
    public static void clear() {
        indentMap.clear();
    }

    /**
     * 指定された文(またはブロック)内の各文に対してインデント情報を記録する
     * @param stmt 記録対象の文
     * @param indent インデント文字列
     */
    public static void recordIndent(Statement stmt, String indent) {
        // このメソッドはもう使用しない
    }

    /**
     * 指定された行番号にインデント情報を記録する
     * @param line 行番号
     * @param indent インデント文字列
     */
    public static void recordIndentForLine(int line, String indent) {
        if (line != -1) {
            indentMap.put(line, indent);
        }
    }

    public static String getIndentForLine(int line) {
        return indentMap.getOrDefault(line, "");
    }

    public static String calculateIndent(Node node) {
        // このメソッドはもう使用しない
        return "";
    }
}