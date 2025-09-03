import { execSync } from 'child_process';
import * as fs from 'fs';
import * as path from 'path';
import * as vscode from 'vscode';

export function activate(context: vscode.ExtensionContext) { //拡張機能有効時
    const provider = new JavaToNadeshikoViewProvider(); //ビューのインスタンス生成
    context.subscriptions.push( //ビューの登録
        vscode.window.registerWebviewViewProvider('javaToNadeshikoView', provider)
    );
    context.subscriptions.push( //エディタ変更時ビューを更新
        vscode.window.onDidChangeActiveTextEditor(() => provider.updateContent())
    );
    context.subscriptions.push( //ドキュメント内容が変更されたらビューを更新
        vscode.workspace.onDidChangeTextDocument((event) => {
            const activeEditor = vscode.window.activeTextEditor;
            if (activeEditor?.document === event.document && event.document.languageId === 'java') { // Javaファイルがアクティブな場合
                provider.updateContent();
            }
        })
    );
}

class JavaToNadeshikoViewProvider implements vscode.WebviewViewProvider { 
    private _view?: vscode.WebviewView; //ビューの参照を保持

    resolveWebviewView(webviewView: vscode.WebviewView) { //ビューを初めて表示するとき
        this._view = webviewView;
        webviewView.webview.options = { enableScripts: true }; //ビュー上でJavaScriptを有効化
        webviewView.webview.html = this.getHtml(); //ビューのHTMLを設定
        this.updateContent();
    }

    updateContent() {
        if (!this._view) { // ビューが存在しない場合
            return;
        }
        const activeEditor = vscode.window.activeTextEditor; //アクティブなエディタを取得
        if (!activeEditor || activeEditor.document.languageId !== 'java') { // アクティブなエディタがない、または Java ファイルでない場合
            this._view.webview.postMessage({ type: 'update', content: 'Javaファイルを開いてください' });
            return;
        }
        const javaCode = activeEditor.document.getText(); //Javaコードを取得
        const nadeshikoCode = this.convertJavaToNadeshiko(javaCode); //なでしこコードに変換
        this._view.webview.postMessage({ type: 'update', content: nadeshikoCode });
    }

    private convertJavaToNadeshiko(javaCode: string): string {
        const baseDir = '/Users/yuha-kut/java-to-nadeshiko-converter/src'; //変換プログラムが置かれているディレクトリ
        const javaFile = 'JavaToNadeshikoConverter.java'; //変換プログラムのファイル名
        const jarFile = 'javaparser-core-3.26.2.jar'; //JavaParserのライブラリ
        const tempFilePath = path.join(baseDir, 'temp_java_code.txt'); //Javaコードを保存するファイルパス
        try {
            fs.writeFileSync(tempFilePath, javaCode, 'utf8'); //Javaコードをファイルに保存
            const result = execSync( //コンパイル
                `javac -cp ".:${jarFile}" ${javaFile}`,
                { cwd: baseDir, encoding: 'utf8' }
            );
            const output = execSync( //実行
                `java -cp ".:${jarFile}" JavaToNadeshikoConverter "${tempFilePath}"`,
                { cwd: baseDir, encoding: 'utf8' }
            );
            fs.unlinkSync(tempFilePath); //ファイルを削除
            return output.trim() || 'まだ変換に対応していないJavaコードです';
        } catch (err: any) { //エラー処理
            return `変換エラー: ${err.message || err}`;
        }
    }

    getHtml(): string {
        return `<!DOCTYPE html>
<html lang="ja"><head><meta charset="UTF-8"><title>Java→なでしこ変換</title></head>
<body>
    <h2>Java→なでしこ 変換結果</h2>
    <div id="result" style="white-space:pre-wrap;font-family:monospace;">Javaファイルを開いてください</div>
    <script>
        window.addEventListener('message', event => {
            if (event.data.type === 'update') {
                document.getElementById('result').textContent = event.data.content;
            }
        });
    </script>
</body></html>`;
    }
}