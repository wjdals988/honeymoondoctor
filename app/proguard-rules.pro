# 예약번호/PIN/바우처 등 민감 필드가 담긴 데이터 클래스는 난독화되어도 동작에는 문제 없으나,
# 크래시 리포트를 사용하지 않으므로(Crashlytics 미사용) 별도 keep 규칙은 최소화한다.

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions
