import type { InputHTMLAttributes, LabelHTMLAttributes } from "react";

interface FieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
}

export function Field({ label, id, className = "", ...rest }: FieldProps) {
  const fieldId = id ?? label.toLowerCase().replace(/\s+/g, "-");
  return (
    <div className="field">
      <label htmlFor={fieldId}>{label}</label>
      <input id={fieldId} className={`input ${className}`.trim()} {...rest} />
    </div>
  );
}

export function Label(props: LabelHTMLAttributes<HTMLLabelElement>) {
  return <label {...props} />;
}
