package com.babybuddypocket.app;

/** Labels only: Baby Buddy stores plain numbers without enforcing or reporting units. */
public final class Units {
  public final boolean ounces, pounds, inches, fahrenheit;

  public Units(boolean ounces, boolean pounds, boolean inches, boolean fahrenheit) {
    this.ounces = ounces;
    this.pounds = pounds;
    this.inches = inches;
    this.fahrenheit = fahrenheit;
  }

  public String label(String endpoint, String field) {
    switch (field) {
      case "amount":
        return endpoint.equals("feedings") || endpoint.equals("pumping")
            ? ounces ? " fl oz" : " ml"
            : "";
      case "weight":
        return pounds ? " lb" : " kg";
      case "height":
      case "head_circumference":
        return inches ? " in" : " cm";
      case "temperature":
        return fahrenheit ? " °F" : " °C";
      default:
        return "";
    }
  }
}
