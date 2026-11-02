import { sqliteTable, text, integer } from 'drizzle-orm/sqlite-core';
export const entries = sqliteTable('entries', {
 id: text('id').primaryKey(), kind: text('kind').notNull(), title: text('title').notNull(),
 payload: text('payload').notNull(), status: text('status').notNull().default('draft'),
 createdBy: text('created_by').notNull(), createdAt: integer('created_at').notNull(), updatedAt: integer('updated_at').notNull()
});
export const files = sqliteTable('files', {
 id:text('id').primaryKey(),name:text('name').notNull(),size:integer('size').notNull(),sha256:text('sha256').notNull(),createdAt:integer('created_at').notNull()
});
export const submissions = sqliteTable('submissions', {
 id:text('id').primaryKey(),kind:text('kind').notNull(),title:text('title').notNull(),payload:text('payload').notNull(),
 contact:text('contact').notNull().default(''),status:text('status').notNull().default('pending'),
 entryId:text('entry_id'),createdAt:integer('created_at').notNull()
});
export const limits = sqliteTable('request_limits', {key:text('key').primaryKey(),count:integer('count').notNull(),expiresAt:integer('expires_at').notNull()});
export const codes = sqliteTable('login_codes', {id:text('id').primaryKey(),digest:text('digest').notNull(),attempts:integer('attempts').notNull().default(0),expiresAt:integer('expires_at').notNull()});
export const sessions = sqliteTable('admin_sessions', {digest:text('digest').primaryKey(),expiresAt:integer('expires_at').notNull()});
export const settings = sqliteTable('private_settings', {key:text('key').primaryKey(),value:text('value').notNull()});
