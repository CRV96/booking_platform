<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>Verify your email</title>
</head>
<body style="margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;">

  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f4;padding:40px 0;">
    <tr>
      <td align="center">
        <table width="600" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.08);">

          <!-- Header -->
          <tr>
            <td style="background:#1a73e8;padding:32px 40px;text-align:center;">
              <h1 style="margin:0;color:#ffffff;font-size:24px;font-weight:700;letter-spacing:0.5px;">
                Booking Platform
              </h1>
            </td>
          </tr>

          <!-- Body -->
          <tr>
            <td style="padding:40px;">
              <p style="margin:0 0 16px;font-size:16px;color:#333333;">
                Hi <strong>${user.firstName!""}</strong>,
              </p>
              <p style="margin:0 0 24px;font-size:15px;color:#555555;line-height:1.6;">
                Thanks for registering. Please verify your email address to activate your account.
                This link expires in <strong>${linkExpirationFormatter(linkExpiration)}</strong>.
              </p>

              <!-- Button -->
              <table cellpadding="0" cellspacing="0" style="margin:0 0 32px;">
                <tr>
                  <td style="background:#1a73e8;border-radius:6px;">
                    <a href="${link}"
                       style="display:inline-block;padding:14px 32px;color:#ffffff;font-size:15px;font-weight:600;text-decoration:none;border-radius:6px;">
                      Verify Email Address
                    </a>
                  </td>
                </tr>
              </table>

              <p style="margin:0 0 8px;font-size:13px;color:#888888;">
                If the button doesn't work, copy and paste this link into your browser:
              </p>
              <p style="margin:0 0 32px;font-size:13px;word-break:break-all;">
                <a href="${link}" style="color:#1a73e8;">${link}</a>
              </p>

              <p style="margin:0;font-size:13px;color:#aaaaaa;">
                If you didn't create an account, you can safely ignore this email.
              </p>
            </td>
          </tr>

          <!-- Footer -->
          <tr>
            <td style="background:#f9f9f9;padding:20px 40px;text-align:center;border-top:1px solid #eeeeee;">
              <p style="margin:0;font-size:12px;color:#aaaaaa;">
                &copy; ${.now?string('yyyy')} Booking Platform. All rights reserved.
              </p>
            </td>
          </tr>

        </table>
      </td>
    </tr>
  </table>

</body>
</html>
