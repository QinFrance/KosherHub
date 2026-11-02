declare namespace Cloudflare {
  interface Env {
    DB?: D1Database;
    BUCKET?: R2Bucket;
  }
}
declare namespace Cloudflare { interface Env { ADMIN_USER_ID?: string; } }
declare namespace Cloudflare { interface Env { ADMIN_EMAIL?: string; AUTH_SECRET?: string; RESEND_API_KEY?: string; EMAIL_FROM?: string; } }
