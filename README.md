# vcmc-openinv

[vcmc.jp](https://vcmc.jp) サーバー向け管理プラグイン。  
プレイヤーのインベントリ・エンダーチェストをオフライン中でも閲覧・編集でき、死亡前インベントリの復元にも対応しています。

**Folia 対応** / Paper 1.21.4+ (API 26.1)

## コマンド

| コマンド | エイリアス | 説明 |
|---|---|---|
| `/openinv <player>` | `/oi` | プレイヤーのインベントリを開く（オフライン対応） |
| `/openender <player>` | `/oe` | プレイヤーのエンダーチェストを開く（オフライン対応） |
| `/restoreinv <player> [index] [confirm]` | `/ri` | 死亡前インベントリを復元する |

## 権限

| 権限ノード | デフォルト | 説明 |
|---|---|---|
| `openinv.openinv` | op | インベントリを開く |
| `openinv.openender` | op | エンダーチェストを開く |
| `openinv.restoreinv` | op | 死亡前インベントリを復元する |
| `openinv.*` | op | 全権限 |

## ダウンロード

[Releases](https://github.com/ShotaXiangtai/vcmc-openinv/releases) から最新の JAR をダウンロードしてください。

## ビルド

```
mvn package
```

`target/vcmc-openinv-<version>.jar` が生成されます。

## ライセンス

Public Domain — [UNLICENSE](UNLICENSE)
