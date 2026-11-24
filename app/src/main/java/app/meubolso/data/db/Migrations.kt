package app.meubolso.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrações versionadas do banco. Cada versão nova do esquema ganha uma Migration aqui
 * (nunca usamos fallbackToDestructiveMigration: os dados do usuário não podem sumir).
 * Os esquemas JSON de cada versão ficam em app/schemas/ e o SQL abaixo é conferido
 * contra eles por scripts/check_migrations.py.
 */
object Migrations {
    /** v2: registro de exclusões com justificativa + poupança. */
    val M1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `deleted_transactions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `original_id` INTEGER NOT NULL, `type` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `date` INTEGER NOT NULL, `description` TEXT NOT NULL, `payment_method` TEXT, `origin` TEXT NOT NULL, `created_at` INTEGER NOT NULL, `reason` TEXT NOT NULL, `deleted_at` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_deleted_transactions_deleted_at` ON `deleted_transactions` (`deleted_at`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `savings_movements` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `kind` TEXT NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_savings_movements_date` ON `savings_movements` (`date`)")
        }
    }

    /** v3: contas fixas, pagamentos, entradas recorrentes, recebimentos e controle de notificações. */
    val M2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `bills` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `due_day` INTEGER NOT NULL, `interval_months` INTEGER NOT NULL, `anchor_month` TEXT NOT NULL, `payment_method` TEXT, `remind_days_before` INTEGER NOT NULL, `keywords` TEXT NOT NULL, `is_subscription` INTEGER NOT NULL, `active` INTEGER NOT NULL, `is_sample` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bills_category_id` ON `bills` (`category_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `bill_payments` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bill_id` INTEGER NOT NULL, `period` TEXT NOT NULL, `amount` INTEGER NOT NULL, `paid_date` INTEGER NOT NULL, `transaction_id` INTEGER, FOREIGN KEY(`bill_id`) REFERENCES `bills`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bill_payments_bill_id_period` ON `bill_payments` (`bill_id`, `period`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_bill_payments_transaction_id` ON `bill_payments` (`transaction_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `recurring_incomes` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `amount` INTEGER NOT NULL, `category_id` INTEGER NOT NULL, `day` INTEGER NOT NULL, `anchor_month` TEXT NOT NULL, `active` INTEGER NOT NULL, `is_sample` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`category_id`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_incomes_category_id` ON `recurring_incomes` (`category_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `income_receipts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `income_id` INTEGER NOT NULL, `period` TEXT NOT NULL, `amount` INTEGER NOT NULL, `transaction_id` INTEGER, FOREIGN KEY(`income_id`) REFERENCES `recurring_incomes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_income_receipts_income_id_period` ON `income_receipts` (`income_id`, `period`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_income_receipts_transaction_id` ON `income_receipts` (`transaction_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `notified` (`key` TEXT NOT NULL, `at` INTEGER NOT NULL, PRIMARY KEY(`key`))")
        }
    }

    /** v4: contas de valor variável + lista de compras (seções, catálogo, lista, compras, histórico). */
    val M3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `bills` ADD COLUMN `variable_amount` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("CREATE TABLE IF NOT EXISTS `market_sections` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sort_order` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `section_id` INTEGER NOT NULL, `unit` TEXT NOT NULL, `is_sample` INTEGER NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`section_id`) REFERENCES `market_sections`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_items_section_id` ON `shopping_items` (`section_id`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_items_name` ON `shopping_items` (`name`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_list` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `item_id` INTEGER NOT NULL, `quantity` TEXT NOT NULL, `checked` INTEGER NOT NULL, `price` INTEGER, `added_at` INTEGER NOT NULL, `checked_at` INTEGER, FOREIGN KEY(`item_id`) REFERENCES `shopping_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_shopping_list_item_id` ON `shopping_list` (`item_id`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `shopping_trips` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` INTEGER NOT NULL, `total` INTEGER NOT NULL, `payment_method` TEXT, `transaction_id` INTEGER, `item_count` INTEGER NOT NULL, `is_sample` INTEGER NOT NULL, FOREIGN KEY(`transaction_id`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_trips_transaction_id` ON `shopping_trips` (`transaction_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_shopping_trips_date` ON `shopping_trips` (`date`)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `purchase_history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `item_id` INTEGER NOT NULL, `date` INTEGER NOT NULL, `price` INTEGER, `quantity` TEXT NOT NULL, `trip_id` INTEGER, FOREIGN KEY(`item_id`) REFERENCES `shopping_items`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`trip_id`) REFERENCES `shopping_trips`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_item_id` ON `purchase_history` (`item_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_history_trip_id` ON `purchase_history` (`trip_id`)")
        }
    }

    /**
     * v5: carteiras (conta, cartão de crédito, vale, dinheiro) e transferências entre elas.
     * Os lançamentos antigos vão para "Conta", menos os pagos em dinheiro, que vão para "Dinheiro"
     * (e os de cartão, se houver, para um cartão criado aqui).
     */
    val M4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallets` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `kind` TEXT NOT NULL, `limit_amount` INTEGER NOT NULL, `closing_day` INTEGER NOT NULL, `due_day` INTEGER NOT NULL, `recharge_day` INTEGER NOT NULL, `initial_balance` INTEGER NOT NULL, `color` INTEGER NOT NULL, `sort_order` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `created_at` INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE IF NOT EXISTS `wallet_transfers` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `from_wallet_id` INTEGER NOT NULL, `to_wallet_id` INTEGER NOT NULL, `amount` INTEGER NOT NULL, `date` INTEGER NOT NULL, `note` TEXT NOT NULL, `created_at` INTEGER NOT NULL, FOREIGN KEY(`from_wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`to_wallet_id`) REFERENCES `wallets`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfers_from_wallet_id` ON `wallet_transfers` (`from_wallet_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfers_to_wallet_id` ON `wallet_transfers` (`to_wallet_id`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_wallet_transfers_date` ON `wallet_transfers` (`date`)")
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `wallet_id` INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_wallet_id` ON `transactions` (`wallet_id`)")
            db.execSQL("ALTER TABLE `deleted_transactions` ADD COLUMN `wallet_id` INTEGER")

            // Carteiras padrão (cores iguais às de DefaultWallets).
            db.execSQL("INSERT INTO `wallets` (`id`, `name`, `kind`, `limit_amount`, `closing_day`, `due_day`, `recharge_day`, `initial_balance`, `color`, `sort_order`, `archived`, `created_at`) VALUES (1, 'Conta', 'ACCOUNT', 0, 0, 0, 0, 0, 4279592384, 0, 0, CAST(strftime('%s', 'now') AS INTEGER) * 1000)")
            db.execSQL("INSERT INTO `wallets` (`id`, `name`, `kind`, `limit_amount`, `closing_day`, `due_day`, `recharge_day`, `initial_balance`, `color`, `sort_order`, `archived`, `created_at`) VALUES (2, 'Dinheiro', 'CASH', 0, 0, 0, 0, 0, 4281236786, 1, 0, CAST(strftime('%s', 'now') AS INTEGER) * 1000)")
            db.execSQL("INSERT INTO `wallets` (`id`, `name`, `kind`, `limit_amount`, `closing_day`, `due_day`, `recharge_day`, `initial_balance`, `color`, `sort_order`, `archived`, `created_at`) SELECT 3, 'Cartão de crédito', 'CREDIT_CARD', 0, 1, 10, 0, 0, 4285143962, 2, 0, CAST(strftime('%s', 'now') AS INTEGER) * 1000 WHERE EXISTS (SELECT 1 FROM `transactions` WHERE `payment_method` = 'CARTAO')")
            db.execSQL("UPDATE `transactions` SET `wallet_id` = CASE `payment_method` WHEN 'DINHEIRO' THEN 2 WHEN 'CARTAO' THEN 3 ELSE 1 END")
            db.execSQL("UPDATE `deleted_transactions` SET `wallet_id` = CASE `payment_method` WHEN 'DINHEIRO' THEN 2 WHEN 'CARTAO' THEN 3 ELSE 1 END")
        }
    }

    val ALL: Array<Migration> = arrayOf(M1_2, M2_3, M3_4, M4_5)
}
