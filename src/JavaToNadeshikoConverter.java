import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;

import converter.CommentConverter;
import converter.PrintlnConverter;

public class JavaToNadeshikoConverter {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Javaファイル名を指定してください");
            return;
        }
        String javaCode = new String(Files.readAllBytes(Paths.get(args[0]))); //ファイルを文字列に変換
        CompilationUnit cu = StaticJavaParser.parse(javaCode); //構文木を生成

        List<CommentConverter.Item> comments = CommentConverter.convert(cu); //コメントを変換
        List<PrintlnConverter.Item> prints = PrintlnConverter.convert(cu); //System.out.println文を変換

        List<Item> all = new ArrayList<>(); //全ての変換結果を格納するリスト
        for (CommentConverter.Item c : comments) all.add(new Item(c.line, c.content));
        for (PrintlnConverter.Item p : prints) all.add(new Item(p.line, p.content));

        all.sort(Comparator.comparingInt(i -> i.line)); // 行番号順でソート

        for (Item i : all) { // 出力
            System.out.println(i.content);
        }
    }
    static class Item { // 行番号と文字列を保持するためのクラス
        int line;
        String content;
        Item(int line, String content) { this.line = line; this.content = content; }
    }
}